import math
import re
from dataclasses import dataclass
from pathlib import Path

from rank_bm25 import BM25Okapi

from app.schemas.documents import DocumentIn, SearchResult

_DOC_ID_PATTERN = re.compile(r"^Doc ID:\s*(\S+)", re.MULTILINE)
_TITLE_PATTERN = re.compile(r"^#\s+(.+)$", re.MULTILINE)
_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")

# Standard English function words. These carry no topical content in any
# corpus, so removing them is safe regardless of how big or small the KB is
# -- unlike the per-corpus dynamic IDF fix below, which only catches a word
# once it happens to be common *in this specific corpus*. A small KB can
# easily contain an ordinary word (e.g. "was") in only one document purely
# by chance of writing style, giving that word a genuinely positive,
# non-floored IDF that BM25 will legitimately treat as a strong, distinctive
# signal for that one document -- even though the word means nothing on its
# own. Verified case: "was" appears in exactly 1 of the 8 real KB docs
# (KB-ADVERSARIAL-001, written in past-tense prose), which without this
# filter let an ordinary damaged-item ticket ("...the package was
# delivered...") retrieve and cite the prompt-injection decoy document.
# This list and the dynamic per-corpus fix are complementary, not
# redundant: this one removes words that are *never* meaningful; the
# dynamic fix removes words that happen to be near-universal *in this
# particular KB* (e.g. "policy", "support") but are meaningful in general.
_STOPWORDS = frozenset({
    "a", "an", "the", "and", "or", "but", "if", "of", "at", "by", "for",
    "with", "about", "against", "between", "into", "through", "during",
    "before", "after", "above", "below", "to", "from", "up", "down", "in",
    "out", "on", "off", "over", "under", "again", "further", "then",
    "once", "here", "there", "when", "where", "why", "how", "all", "any",
    "both", "each", "few", "more", "most", "other", "some", "such", "no",
    "nor", "not", "only", "own", "same", "so", "than", "too", "very",
    "is", "am", "are", "was", "were", "be", "been", "being", "have",
    "has", "had", "having", "do", "does", "did", "doing", "will", "would",
    "shall", "should", "can", "could", "may", "might", "must", "this",
    "that", "these", "those", "i", "me", "my", "myself", "we", "our",
    "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
    "he", "him", "his", "himself", "she", "her", "hers", "herself", "it",
    "its", "itself", "they", "them", "their", "theirs", "themselves",
    "what", "which", "who", "whom", "as", "until", "while",
})


def _tokenize(text: str) -> list[str]:
    return [tok for tok in _TOKEN_PATTERN.findall(text.lower()) if tok not in _STOPWORDS]


def _zero_out_floored_idf(bm25: BM25Okapi, corpus: list[list[str]]) -> None:
    """rank_bm25's BM25Okapi floors any term's negative IDF (a term present
    in more than half the corpus) to a small *positive* epsilon instead of
    leaving it negative -- so a query sharing only a near-universal word
    (a common English stopword, or KB-document boilerplate like "policy"/
    "version"/"support") gets a nonzero, positive score against every
    document containing that word, defeating "search() returns [] for a
    genuinely unrelated query", which the rest of the system (grounded-draft
    escalation) depends on. A hand-maintained stopword list only patches the
    specific words on the list and silently reopens for any other word that
    happens to be common in this particular corpus (verified: KB metadata
    words like "policy"/"version" let the adversarial decoy doc rank #1 for
    an off-topic query). Recompute each term's raw (pre-floor) document
    frequency here and zero its IDF in the already-built index whenever that
    raw IDF was negative, so a match on any near-universal term -- whatever
    word that turns out to be for a given corpus -- contributes 0 score
    instead of a small positive one. No stopword list to maintain.
    """
    doc_count = len(corpus)
    document_frequency: dict[str, int] = {}
    for tokens in corpus:
        for term in set(tokens):
            document_frequency[term] = document_frequency.get(term, 0) + 1
    for term, freq in document_frequency.items():
        raw_idf = math.log(doc_count - freq + 0.5) - math.log(freq + 0.5)
        if raw_idf < 0:
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
        corpus = [_tokenize(self._documents[doc_id].content) for doc_id in self._doc_order]
        if not corpus:
            self._bm25 = None
            return
        bm25 = BM25Okapi(corpus)
        _zero_out_floored_idf(bm25, corpus)
        self._bm25 = bm25

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
