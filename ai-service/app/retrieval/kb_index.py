import logging
import re
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import chromadb
from rank_bm25 import BM25Okapi

from app.adapters.embedding_adapter import EmbeddingAdapter
from app.retrieval.lexical import raw_idf, tokenize
from app.schemas.documents import DocumentIn, SearchResult

logger = logging.getLogger(__name__)

_DOC_ID_PATTERN = re.compile(r"^Doc ID:\s*(\S+)", re.MULTILINE)
_TITLE_PATTERN = re.compile(r"^#\s+(.+)$", re.MULTILINE)

_RRF_K = 60
_DEFAULT_SIMILARITY_THRESHOLD = 0.35


def _zero_out_floored_idf(bm25: BM25Okapi, corpus: list[list[str]]) -> None:
    """rank_bm25's BM25Okapi floors any term's negative IDF (a term present
    in more than half the corpus) to a small *positive* epsilon instead of
    leaving it negative -- so a query sharing only a near-universal word
    (a common English stopword, or KB-document boilerplate like "policy"/
    "version"/"support") gets a nonzero, positive score against every
    document containing that word, defeating "search() returns [] for a
    genuinely unrelated query", which the rest of the system (grounded-draft
    escalation) depends on. Recompute each term's raw (pre-floor) document
    frequency here and zero its IDF in the already-built index whenever that
    raw IDF was negative, so a match on any near-universal term -- whatever
    word that turns out to be for a given corpus -- contributes 0 score
    instead of a small positive one.
    """
    doc_count = len(corpus)
    document_frequency: dict[str, int] = {}
    for tokens in corpus:
        for term in set(tokens):
            document_frequency[term] = document_frequency.get(term, 0) + 1
    for term, freq in document_frequency.items():
        if raw_idf(freq, doc_count) < 0:
            bm25.idf[term] = 0.0


def _rrf_fuse(*ranked_lists: list[str]) -> dict[str, float]:
    """Reciprocal Rank Fusion: combine several ranked doc_id lists into one
    score per doc_id, using each list's *rank* rather than its raw score --
    BM25 scores and cosine similarities live on unrelated scales, so fusing
    by rank avoids having to normalize them against each other."""
    scores: dict[str, float] = {}
    for ranked_ids in ranked_lists:
        for rank, doc_id in enumerate(ranked_ids, start=1):
            scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (_RRF_K + rank)
    return scores


@dataclass
class _Document:
    doc_id: str
    title: str
    content: str


class KBIndex:
    def __init__(
        self,
        embedding_adapter: EmbeddingAdapter,
        similarity_threshold: float = _DEFAULT_SIMILARITY_THRESHOLD,
    ) -> None:
        self._embedding_adapter = embedding_adapter
        self._similarity_threshold = similarity_threshold
        self._documents: dict[str, _Document] = {}
        self._bm25: BM25Okapi | None = None
        self._doc_order: list[str] = []
        self._chroma_collection: Any = None
        # chromadb.EphemeralClient() shares one process-wide in-memory system
        # across every instance it creates -- its "system identifier" is
        # hardcoded to the literal string "ephemeral" regardless of how many
        # separate EphemeralClient() calls are made (see chromadb's
        # SharedSystemClient._get_identifier_from_settings). A fixed
        # collection name like "kb_docs" would therefore resolve to the same
        # underlying collection across *every* KBIndex instance in one
        # process, causing dimension mismatches and stale documents leaking
        # between unrelated indexes (e.g. between two different tests, or
        # between a router's long-lived singleton and a test's throwaway
        # index). Minting this name once per instance (not once per rebuild)
        # keeps every KBIndex's embedding data isolated from every other
        # instance while still letting _rebuild_index() delete-then-recreate
        # its own single collection on each rebuild, instead of leaking a
        # fresh never-deleted collection on every ingest() call.
        self._collection_name = f"kb_docs_{uuid.uuid4().hex}"
        self._chroma_client = chromadb.EphemeralClient()

    def load_directory(self, directory: str) -> int:
        path = Path(directory)
        if not path.exists():
            return 0
        count = 0
        for md_file in sorted(path.glob("*.md")):
            text = md_file.read_text(encoding="utf-8")
            doc_id_match = _DOC_ID_PATTERN.search(text)
            if not doc_id_match:
                continue
            title_match = _TITLE_PATTERN.search(text)
            doc_id = doc_id_match.group(1)
            title = title_match.group(1).strip() if title_match else doc_id
            self._documents[doc_id] = _Document(doc_id=doc_id, title=title, content=text)
            count += 1
        self._rebuild_index()
        return count

    def ingest(self, documents: list[DocumentIn]) -> list[str]:
        ids = []
        for doc in documents:
            self._documents[doc.doc_id] = _Document(doc_id=doc.doc_id, title=doc.title, content=doc.content)
            ids.append(doc.doc_id)
        self._rebuild_index()
        return ids

    def _rebuild_index(self) -> None:
        self._doc_order = list(self._documents.keys())
        corpus = [tokenize(self._documents[doc_id].content) for doc_id in self._doc_order]
        if not corpus:
            self._bm25 = None
            self._chroma_collection = None
            return
        bm25 = BM25Okapi(corpus)
        _zero_out_floored_idf(bm25, corpus)
        self._bm25 = bm25

        contents = [self._documents[doc_id].content for doc_id in self._doc_order]
        try:
            embeddings = self._embedding_adapter.embed_documents(contents)
        except Exception:
            # The embedding provider is an external network call (OpenAI/
            # OpenRouter) that can fail independently of everything else
            # this service does -- an invalid/expired API key, a rate limit,
            # or a transient outage. documents.py builds this index's
            # module-level singleton at *import* time, so letting this
            # propagate used to crash the entire ai-service process before
            # it could even start, taking down completely unrelated routes
            # (e.g. /internal/triage, which never touches embeddings) along
            # with it. Degrade to BM25-only search for this rebuild instead:
            # _embedding_ranked_ids() already treats a None collection as
            # "no embedding results" and search() still works via BM25.
            logger.exception(
                "Embedding provider failed while rebuilding KB index; "
                "degrading to BM25-only search until the next successful rebuild."
            )
            self._chroma_collection = None
            return
        # Delete this instance's previous collection (if any) before
        # recreating it, so a long-lived KBIndex (e.g. the router's
        # module-level singleton, rebuilt on every ingest() call) holds at
        # most one live chromadb collection at a time instead of minting a
        # brand-new, never-deleted one -- and its full embedded corpus --
        # on every rebuild.
        try:
            self._chroma_client.delete_collection(self._collection_name)
        except Exception:
            pass  # no collection yet on the first rebuild
        collection = self._chroma_client.create_collection(
            name=self._collection_name, metadata={"hnsw:space": "cosine"},
        )
        collection.add(ids=self._doc_order, embeddings=embeddings)
        self._chroma_collection = collection

    def _bm25_ranked_ids(self, query: str, k: int) -> list[str]:
        scores = self._bm25.get_scores(tokenize(query))
        ranked = sorted(zip(self._doc_order, scores), key=lambda pair: pair[1], reverse=True)
        return [doc_id for doc_id, score in ranked if score > 0][:k]

    def _embedding_ranked_ids(self, query: str, k: int) -> list[str]:
        if self._chroma_collection is None:
            return []
        query_embedding = self._embedding_adapter.embed_query(query)
        if not any(query_embedding):
            # A zero vector means the query carried no discriminative signal
            # (e.g. every token was near-universal and got zero-floored) --
            # skip nearest-neighbor search rather than returning an
            # arbitrary "closest" doc for a query that matches nothing.
            return []
        n_results = min(k, len(self._doc_order))
        result = self._chroma_collection.query(query_embeddings=[query_embedding], n_results=n_results)
        ranked_ids = []
        for doc_id, distance in zip(result["ids"][0], result["distances"][0]):
            similarity = 1 - distance
            if similarity >= self._similarity_threshold:
                ranked_ids.append(doc_id)
        return ranked_ids

    def search(self, query: str, k: int = 5) -> list[SearchResult]:
        if not self._bm25 or not self._doc_order:
            return []

        bm25_ranked_ids = self._bm25_ranked_ids(query, k)
        embedding_ranked_ids = self._embedding_ranked_ids(query, k)

        fused_scores = _rrf_fuse(bm25_ranked_ids, embedding_ranked_ids)
        top_ids = sorted(fused_scores, key=lambda doc_id: fused_scores[doc_id], reverse=True)[:k]

        results = []
        for doc_id in top_ids:
            doc = self._documents[doc_id]
            results.append(SearchResult(
                doc_id=doc.doc_id,
                title=doc.title,
                snippet=doc.content[:200].replace("\n", " ").strip(),
                score=round(fused_scores[doc_id], 4),
            ))
        return results
