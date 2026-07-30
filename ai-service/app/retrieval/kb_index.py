import re
from dataclasses import dataclass
from pathlib import Path

from rank_bm25 import BM25Okapi

from app.retrieval.lexical import raw_idf, tokenize
from app.schemas.documents import DocumentIn, SearchResult

_DOC_ID_PATTERN = re.compile(r"^Doc ID:\s*(\S+)", re.MULTILINE)
_TITLE_PATTERN = re.compile(r"^#\s+(.+)$", re.MULTILINE)


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


@dataclass
class _Document:
    doc_id: str
    title: str
    content: str


class KBIndex:
    def __init__(self) -> None:
        self._documents: dict[str, _Document] = {}
        self._bm25: BM25Okapi | None = None
        self._doc_order: list[str] = []

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
            return
        bm25 = BM25Okapi(corpus)
        _zero_out_floored_idf(bm25, corpus)
        self._bm25 = bm25

    def search(self, query: str, k: int = 5) -> list[SearchResult]:
        if not self._bm25 or not self._doc_order:
            return []
        scores = self._bm25.get_scores(tokenize(query))
        ranked = sorted(zip(self._doc_order, scores), key=lambda pair: pair[1], reverse=True)
        results = []
        for doc_id, score in ranked[:k]:
            if score <= 0:
                continue
            doc = self._documents[doc_id]
            results.append(SearchResult(
                doc_id=doc.doc_id,
                title=doc.title,
                snippet=doc.content[:200].replace("\n", " ").strip(),
                score=round(float(score), 4),
            ))
        return results
