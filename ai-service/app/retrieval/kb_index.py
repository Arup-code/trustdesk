import re
from dataclasses import dataclass
from pathlib import Path

from rank_bm25 import BM25Okapi

from app.schemas.documents import DocumentIn, SearchResult

_DOC_ID_PATTERN = re.compile(r"^Doc ID:\s*(\S+)", re.MULTILINE)
_TITLE_PATTERN = re.compile(r"^#\s+(.+)$", re.MULTILINE)
_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")

# rank_bm25's BM25Okapi floors any word's IDF that goes negative (i.e. any
# word present in more than half the corpus) to a small *positive* epsilon
# rather than leaving it negative. Without stopword removal, an extremely
# common word like "to" can appear in most knowledge-base documents, so a
# query sharing only that one word with the corpus still gets a nonzero
# score against every document containing it -- defeating the "search()
# returns [] for genuinely unrelated queries" relevance filter that the
# rest of the system (grounded-draft escalation) depends on. Filtering
# common stopwords before indexing/querying keeps only content-bearing
# terms as match signals.
_STOPWORDS = frozenset({
    "a", "an", "the", "and", "or", "but", "if", "of", "at", "by", "for",
    "with", "about", "to", "from", "in", "on", "is", "are", "was", "were",
    "be", "been", "being", "this", "that", "these", "those", "i", "you",
    "he", "she", "it", "we", "they", "my", "your", "his", "her", "its",
    "our", "their", "will", "would", "can", "could", "should", "just",
    "so", "as", "not", "do", "does", "did", "have", "has", "had",
})


def _tokenize(text: str) -> list[str]:
    return [tok for tok in _TOKEN_PATTERN.findall(text.lower()) if tok not in _STOPWORDS]


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
        corpus = [_tokenize(self._documents[doc_id].content) for doc_id in self._doc_order]
        self._bm25 = BM25Okapi(corpus) if corpus else None

    def search(self, query: str, k: int = 5) -> list[SearchResult]:
        if not self._bm25 or not self._doc_order:
            return []
        scores = self._bm25.get_scores(_tokenize(query))
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
