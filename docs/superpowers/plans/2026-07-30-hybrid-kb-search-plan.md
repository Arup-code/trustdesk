# Hybrid BM25 + Embedding KB Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add embedding-based semantic search to `ai-service`'s KB retrieval and fuse it with the existing BM25 keyword search via Reciprocal Rank Fusion, without weakening the existing "no relevant docs → refuse" guardrail.

**Architecture:** `KBIndex` gains a second, Chroma-backed (in-memory, ephemeral) vector index alongside its existing `rank_bm25` index, rebuilt together on every load/ingest. `search()` queries both, filters embedding hits below a similarity threshold, and fuses the two ranked lists by RRF. A new `EmbeddingAdapter` protocol (mirroring the existing `ModelAdapter` protocol) provides a deterministic offline `MockEmbeddingAdapter` for tests/dev and a real `OpenAIEmbeddingAdapter` for production.

**Tech Stack:** Python, `chromadb` (new dependency, in-memory client only), `rank_bm25` (existing), `langchain_openai.OpenAIEmbeddings` (existing package, new usage).

**Spec:** `docs/superpowers/specs/2026-07-30-hybrid-kb-search-design.md`

## Global Constraints

- No persistence: embeddings and the Chroma collection are rebuilt in-memory on every `load_directory`/`ingest` call, exactly like the existing BM25 index. No disk cache.
- `chromadb` version floor: `chromadb>=0.5,<1.0` in `ai-service/requirements.txt`.
- Default embedding similarity threshold: `0.35` (configurable via `settings.embedding_similarity_threshold`).
- RRF constant: `60` (standard value), rank-based fusion — never normalize/blend raw BM25 vs. cosine scores directly.
- New adapters follow the existing mock/real split convention (`MockModelAdapter`/`OpenRouterAdapter` → `MockEmbeddingAdapter`/`OpenAIEmbeddingAdapter`), selected via a settings flag, default `"mock"`.
- OpenRouter does not proxy embeddings — the real embedding adapter calls OpenAI's API directly and needs its own `openai_api_key` setting, separate from `openrouter_api_key`.

---

### Task 1: Extract shared lexical utilities (`tokenize`, `raw_idf`)

**Why:** `MockEmbeddingAdapter` (Task 2) needs the exact same tokenizer and near-universal-term weighting `KBIndex`'s BM25 branch already uses (`_tokenize`, the inline raw-IDF check in `_zero_out_floored_idf`) — otherwise the two branches could disagree on which terms carry signal, breaking the "off-topic query returns nothing" guarantee once both branches are fused. Extract both into a shared module first, refactor `kb_index.py` to use it, with no behavior change.

**Files:**
- Create: `ai-service/app/retrieval/lexical.py`
- Modify: `ai-service/app/retrieval/kb_index.py`
- Test: `ai-service/tests/test_lexical.py`

**Interfaces:**
- Produces: `tokenize(text: str) -> list[str]`, `raw_idf(doc_freq: int, doc_count: int) -> float` in `app.retrieval.lexical`. Later tasks (2, 4) import both.

- [ ] **Step 1: Write the failing test**

Create `ai-service/tests/test_lexical.py`:

```python
from app.retrieval.lexical import raw_idf, tokenize


def test_tokenize_lowercases_and_strips_stopwords():
    assert tokenize("The Damaged Item Was Reported") == ["damaged", "item", "reported"]


def test_tokenize_extracts_alphanumeric_tokens_only():
    assert tokenize("order #12345, refund!") == ["order", "12345", "refund"]


def test_raw_idf_is_negative_for_a_term_in_more_than_half_the_corpus():
    assert raw_idf(doc_freq=3, doc_count=4) < 0


def test_raw_idf_is_positive_for_a_rare_term():
    assert raw_idf(doc_freq=1, doc_count=4) > 0
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `ai-service/`): `pytest tests/test_lexical.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.retrieval.lexical'`

- [ ] **Step 3: Create `app/retrieval/lexical.py`**

```python
import math
import re

_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")

# Standard English function words. These carry no topical content in any
# corpus, so removing them is safe regardless of how big or small the KB is
# -- unlike the per-corpus dynamic IDF fix (raw_idf below), which only
# catches a word once it happens to be common *in this specific corpus*. A
# small KB can easily contain an ordinary word (e.g. "was") in only one
# document purely by chance of writing style, giving that word a genuinely
# positive, non-floored IDF that BM25 will legitimately treat as a strong,
# distinctive signal for that one document -- even though the word means
# nothing on its own. Verified case: "was" appears in exactly 1 of the 8
# real KB docs (KB-ADVERSARIAL-001, written in past-tense prose), which
# without this filter let an ordinary damaged-item ticket ("...the package
# was delivered...") retrieve and cite the prompt-injection decoy document.
# This list and raw_idf() are complementary, not redundant: this one
# removes words that are *never* meaningful; raw_idf() removes words that
# happen to be near-universal *in this particular KB* (e.g. "policy",
# "support") but are meaningful in general.
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


def tokenize(text: str) -> list[str]:
    return [tok for tok in _TOKEN_PATTERN.findall(text.lower()) if tok not in _STOPWORDS]


def raw_idf(doc_freq: int, doc_count: int) -> float:
    """BM25's raw (pre-floor) inverse document frequency for a term with the
    given document frequency across a corpus of doc_count documents.
    Negative when the term appears in more than half the corpus -- i.e. it
    carries no discriminative signal in this corpus. rank_bm25's BM25Okapi
    floors this to a small positive epsilon internally; callers that need
    the true (possibly negative) value -- to zero out near-universal terms,
    or to weight a bag-of-words vector -- use this instead.
    """
    return math.log(doc_count - doc_freq + 0.5) - math.log(doc_freq + 0.5)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_lexical.py -v`
Expected: PASS (4 tests)

- [ ] **Step 5: Refactor `kb_index.py` to use the shared module**

Replace the full contents of `ai-service/app/retrieval/kb_index.py` with:

```python
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
```

- [ ] **Step 6: Run the full existing suite to confirm no regression**

Run (from `ai-service/`): `pytest -v`
Expected: PASS, same test count/results as before this task (this step is a pure refactor — `test_kb_index.py`, `test_documents_router.py`, `test_draft_graph.py`, `test_eval_runner.py` must all still pass unchanged).

- [ ] **Step 7: Commit**

```bash
git add ai-service/app/retrieval/lexical.py ai-service/app/retrieval/kb_index.py ai-service/tests/test_lexical.py
git commit -m "refactor: extract shared tokenizer and raw-IDF helper from KBIndex"
```

---

### Task 2: `EmbeddingAdapter` protocol + `MockEmbeddingAdapter`

**Files:**
- Create: `ai-service/app/adapters/embedding_adapter.py`
- Create: `ai-service/app/adapters/mock_embedding_adapter.py`
- Test: `ai-service/tests/test_mock_embedding_adapter.py`

**Interfaces:**
- Consumes: `tokenize`, `raw_idf` from `app.retrieval.lexical` (Task 1).
- Produces: `EmbeddingAdapter` Protocol (`embed_documents(texts: list[str]) -> list[list[float]]`, `embed_query(text: str) -> list[float]`) and `MockEmbeddingAdapter` implementing it. Task 3's factory and Task 4's `KBIndex`/tests depend on both names.

- [ ] **Step 1: Write the failing test**

Create `ai-service/tests/test_mock_embedding_adapter.py`:

```python
import pytest

from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter


def _cosine(a: list[float], b: list[float]) -> float:
    return sum(x * y for x, y in zip(a, b))


def test_identical_documents_have_cosine_similarity_one():
    # Five documents, not two: with a 2-document corpus, every term's
    # document frequency is either exactly half (zero-weighted by
    # MockEmbeddingAdapter's max(0.0, raw_idf(...)) floor) or 100% (also
    # zero-weighted) -- there is no way for any term to get positive
    # weight, so both vectors would be all-zero and the cosine similarity
    # below would be 0.0/0.0, not 1.0. The shared terms here ("zebras",
    # "striped", "mammals") need doc_freq strictly less than half of
    # doc_count to get positive weight -- freq=2 out of 5 qualifies.
    # (Same pitfall as the "three documents, not two" comment in
    # test_kb_index.py's BM25 tests.)
    adapter = MockEmbeddingAdapter()
    vectors = adapter.embed_documents([
        "zebras are striped mammals",
        "zebras are striped mammals",
        "rockets launch into orbit",
        "quarterly financial reports vary",
        "employees request annual leave",
    ])
    a, b = vectors[0], vectors[1]
    assert _cosine(a, b) == pytest.approx(1.0)


def test_documents_with_disjoint_vocabulary_are_orthogonal():
    # Three documents, not two: each content word must have doc_freq
    # strictly less than half of doc_count to get positive weight (see
    # note above) -- freq=1 out of 3 qualifies; freq=1 out of 2 does not.
    adapter = MockEmbeddingAdapter()
    vectors = adapter.embed_documents([
        "zebras are striped mammals",
        "rockets launch into orbit",
        "quarterly financial reports vary",
    ])
    a, b = vectors[0], vectors[1]
    assert _cosine(a, b) == pytest.approx(0.0)


def test_embed_query_is_more_similar_to_the_matching_document():
    adapter = MockEmbeddingAdapter()
    zebra_doc, rocket_doc, _ = adapter.embed_documents([
        "zebras are striped mammals",
        "rockets launch into orbit",
        "quarterly financial reports vary",
    ])

    query_vector = adapter.embed_query("zebras")

    assert _cosine(query_vector, zebra_doc) > _cosine(query_vector, rocket_doc)


def test_terms_common_to_more_than_half_the_corpus_get_zero_weight():
    adapter = MockEmbeddingAdapter()
    adapter.embed_documents([
        "customer support policy about alpha",
        "customer support policy about bravo",
        "customer support policy about charlie",
        "customer support policy about delta",
    ])

    query_vector = adapter.embed_query("customer support policy about")

    assert all(component == 0.0 for component in query_vector)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_mock_embedding_adapter.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.adapters.mock_embedding_adapter'`

- [ ] **Step 3: Create `app/adapters/embedding_adapter.py`**

```python
from typing import Protocol


class EmbeddingAdapter(Protocol):
    def embed_documents(self, texts: list[str]) -> list[list[float]]: ...

    def embed_query(self, text: str) -> list[float]: ...
```

- [ ] **Step 4: Create `app/adapters/mock_embedding_adapter.py`**

```python
import math

from app.retrieval.lexical import raw_idf, tokenize


class MockEmbeddingAdapter:
    """Deterministic, offline embedding adapter for tests/dev. Builds a
    bag-of-words vector space over whatever corpus embed_documents() last
    saw, weighting each term by the same zero-floored raw IDF formula
    KBIndex's BM25 branch uses for near-universal terms (see
    app.retrieval.lexical.raw_idf) -- so this mock and the BM25 branch
    agree on which terms carry any signal at all, keeping the hybrid
    search's "off-topic query returns nothing" guarantee intact under test.
    embed_query() reuses the vocabulary/weights from the most recent
    embed_documents() call, mirroring how BM25 itself needs corpus-wide
    statistics to score a query.
    """

    def __init__(self) -> None:
        self._vocabulary: dict[str, int] = {}
        self._weights: list[float] = []

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        token_lists = [tokenize(text) for text in texts]
        doc_count = len(token_lists)
        document_frequency: dict[str, int] = {}
        for tokens in token_lists:
            for term in set(tokens):
                document_frequency[term] = document_frequency.get(term, 0) + 1

        terms = sorted(document_frequency)
        self._vocabulary = {term: idx for idx, term in enumerate(terms)}
        self._weights = [max(0.0, raw_idf(document_frequency[term], doc_count)) for term in terms]

        return [self._vectorize(tokens) for tokens in token_lists]

    def embed_query(self, text: str) -> list[float]:
        return self._vectorize(tokenize(text))

    def _vectorize(self, tokens: list[str]) -> list[float]:
        vector = [0.0] * len(self._vocabulary)
        for term in tokens:
            idx = self._vocabulary.get(term)
            if idx is None:
                continue
            vector[idx] += self._weights[idx]
        norm = math.sqrt(sum(v * v for v in vector))
        if norm == 0:
            return vector
        return [v / norm for v in vector]
```

- [ ] **Step 5: Run test to verify it passes**

Run: `pytest tests/test_mock_embedding_adapter.py -v`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add ai-service/app/adapters/embedding_adapter.py ai-service/app/adapters/mock_embedding_adapter.py ai-service/tests/test_mock_embedding_adapter.py
git commit -m "feat: add EmbeddingAdapter protocol and deterministic MockEmbeddingAdapter"
```

---

### Task 3: Settings, embedding adapter factory, and `OpenAIEmbeddingAdapter`

**Files:**
- Modify: `ai-service/app/settings.py`
- Create: `ai-service/app/adapters/openai_embedding_adapter.py`
- Modify: `ai-service/app/adapters/__init__.py`
- Test: `ai-service/tests/test_adapters_factory.py`

**Interfaces:**
- Consumes: `MockEmbeddingAdapter`, `EmbeddingAdapter` (Task 2).
- Produces: `settings.ai_embedding_mode`, `settings.openai_api_key`, `settings.embedding_model`, `settings.embedding_similarity_threshold`; `get_embedding_adapter() -> EmbeddingAdapter` in `app.adapters`. Task 4's callers (`documents.py`, `eval_runner.py`) depend on `get_embedding_adapter` and `settings.embedding_similarity_threshold`.

- [ ] **Step 1: Write the failing test**

Create `ai-service/tests/test_adapters_factory.py`:

```python
from app.adapters import get_embedding_adapter, get_model_adapter
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter


def test_get_model_adapter_defaults_to_mock():
    assert isinstance(get_model_adapter(), MockModelAdapter)


def test_get_embedding_adapter_defaults_to_mock():
    assert isinstance(get_embedding_adapter(), MockEmbeddingAdapter)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_adapters_factory.py -v`
Expected: FAIL with `ImportError: cannot import name 'get_embedding_adapter' from 'app.adapters'`

- [ ] **Step 3: Add settings fields**

In `ai-service/app/settings.py`, add four fields to the `Settings` class (after `openrouter_model`):

```python
    ai_embedding_mode: str = "mock"
    openai_api_key: str = ""
    embedding_model: str = "text-embedding-3-small"
    embedding_similarity_threshold: float = 0.35
```

Full resulting file:

```python
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    data_dir: str = "../data"
    ai_model_mode: str = "mock"
    openrouter_api_key: str = ""
    openrouter_model: str = "openrouter/auto"
    ai_embedding_mode: str = "mock"
    openai_api_key: str = ""
    embedding_model: str = "text-embedding-3-small"
    embedding_similarity_threshold: float = 0.35
    java_base_url: str = "http://localhost:8080"
    internal_api_key: str = "dev-internal-key-change-me"
    eval_java_username: str = "agent1"
    eval_java_password: str = "agent123"


settings = Settings()
```

- [ ] **Step 4: Create `app/adapters/openai_embedding_adapter.py`**

```python
from langchain_openai import OpenAIEmbeddings

from app.settings import settings


class OpenAIEmbeddingAdapter:
    def __init__(self) -> None:
        self._embeddings = OpenAIEmbeddings(
            api_key=settings.openai_api_key,
            model=settings.embedding_model,
        )

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return self._embeddings.embed_documents(texts)

    def embed_query(self, text: str) -> list[float]:
        return self._embeddings.embed_query(text)
```

- [ ] **Step 5: Update `app/adapters/__init__.py`**

Replace its full contents with:

```python
from app.adapters.embedding_adapter import EmbeddingAdapter
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter
from app.adapters.model_adapter import ModelAdapter
from app.adapters.openai_embedding_adapter import OpenAIEmbeddingAdapter
from app.adapters.openrouter_adapter import OpenRouterAdapter
from app.settings import settings


def get_model_adapter() -> ModelAdapter:
    if settings.ai_model_mode == "openrouter":
        return OpenRouterAdapter()
    return MockModelAdapter()


def get_embedding_adapter() -> EmbeddingAdapter:
    if settings.ai_embedding_mode == "openai":
        return OpenAIEmbeddingAdapter()
    return MockEmbeddingAdapter()
```

- [ ] **Step 6: Run test to verify it passes**

Run: `pytest tests/test_adapters_factory.py -v`
Expected: PASS (2 tests)

- [ ] **Step 7: Run the full existing suite to confirm no regression**

Run: `pytest -v`
Expected: PASS, same results as end of Task 1 plus the 6 new tests from Tasks 2–3.

- [ ] **Step 8: Commit**

```bash
git add ai-service/app/settings.py ai-service/app/adapters/openai_embedding_adapter.py ai-service/app/adapters/__init__.py ai-service/tests/test_adapters_factory.py
git commit -m "feat: add embedding settings, OpenAIEmbeddingAdapter, and get_embedding_adapter factory"
```

---

### Task 4: Hybrid `KBIndex` (Chroma + RRF fusion) and wire up all callers

**Files:**
- Modify: `ai-service/requirements.txt`
- Modify: `ai-service/app/retrieval/kb_index.py`
- Modify: `ai-service/app/routers/documents.py`
- Modify: `ai-service/app/eval/eval_runner.py`
- Modify: `ai-service/tests/test_kb_index.py`
- Modify: `ai-service/tests/test_draft_graph.py`
- Modify: `ai-service/tests/test_eval_runner.py`

**Interfaces:**
- Consumes: `EmbeddingAdapter` (Task 2), `MockEmbeddingAdapter` (Task 2), `get_embedding_adapter` + `settings.embedding_similarity_threshold` (Task 3).
- Produces: `KBIndex(embedding_adapter: EmbeddingAdapter, similarity_threshold: float = 0.35)` — constructor signature changes from no-arg to requiring an `EmbeddingAdapter`. `SearchResult.score` now means fused RRF score, not raw BM25 score (no schema change — same field name/type). `_rrf_fuse(*ranked_lists: list[str]) -> dict[str, float]` is a new module-level function in `app.retrieval.kb_index`.

- [ ] **Step 1: Add the `chromadb` dependency**

In `ai-service/requirements.txt`, add a new line after `rank-bm25>=0.2,<1.0`:

```
chromadb>=0.5,<1.0
```

Install it:

Run (from `ai-service/`): `pip install -r requirements.txt`
Expected: `chromadb` and its dependencies install successfully.

- [ ] **Step 2: Write the failing tests — update `test_kb_index.py`**

Replace the full contents of `ai-service/tests/test_kb_index.py` with:

```python
import pytest

from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter
from app.retrieval.kb_index import KBIndex, _rrf_fuse
from app.schemas.documents import DocumentIn


def test_search_ranks_relevant_doc_first(tmp_path):
    # Three documents, not two: with only two documents sharing no query
    # terms, BM25's IDF term degenerates to exactly 0 for any term unique to
    # one document (log((N-n+0.5)/(n+0.5)) with N=2, n=1 is log(1) == 0),
    # so the "irrelevant" filter in KBIndex.search() would drop every result.
    # A third, unrelated document breaks that degenerate tie.
    (tmp_path / "refund.md").write_text(
        "# Refund and Return Policy\n\nDoc ID: KB-REFUND-001\n\n"
        "For damaged or defective items reported within the return window, "
        "support may offer either a replacement or a refund review.",
        encoding="utf-8",
    )
    (tmp_path / "shipping.md").write_text(
        "# Shipping and Delivery Policy\n\nDoc ID: KB-SHIPPING-001\n\n"
        "Most orders ship within 1 business day.",
        encoding="utf-8",
    )
    (tmp_path / "warranty.md").write_text(
        "# Warranty Policy\n\nDoc ID: KB-WARRANTY-001\n\n"
        "Electronics have a 12-month limited warranty from delivery date.",
        encoding="utf-8",
    )

    index = KBIndex(MockEmbeddingAdapter())
    loaded = index.load_directory(str(tmp_path))

    assert loaded == 3
    results = index.search("damaged replacement")
    assert results[0].doc_id == "KB-REFUND-001"
    assert results[0].score > 0


def test_search_filters_out_zero_and_negative_score_results(tmp_path):
    (tmp_path / "refund.md").write_text(
        "# Refund and Return Policy\n\nDoc ID: KB-REFUND-001\n\n"
        "For damaged or defective items reported within the return window, "
        "support may offer either a replacement or a refund review.",
        encoding="utf-8",
    )
    (tmp_path / "shipping.md").write_text(
        "# Shipping and Delivery Policy\n\nDoc ID: KB-SHIPPING-001\n\n"
        "Most orders ship within 1 business day.",
        encoding="utf-8",
    )
    (tmp_path / "warranty.md").write_text(
        "# Warranty Policy\n\nDoc ID: KB-WARRANTY-001\n\n"
        "Electronics have a 12-month limited warranty from delivery date.",
        encoding="utf-8",
    )

    index = KBIndex(MockEmbeddingAdapter())
    index.load_directory(str(tmp_path))

    results = index.search("damaged replacement")
    assert all(r.score > 0 for r in results)
    assert "KB-SHIPPING-001" not in [r.doc_id for r in results]
    assert "KB-WARRANTY-001" not in [r.doc_id for r in results]


def test_search_returns_empty_list_for_empty_index():
    index = KBIndex(MockEmbeddingAdapter())
    assert index.search("anything") == []


def test_search_ignores_terms_common_to_more_than_half_the_corpus(tmp_path):
    # rank_bm25's BM25Okapi floors any term's negative IDF (a term present in
    # more than half the corpus) to a small *positive* epsilon instead of
    # leaving it negative -- so a query built only from words common to every
    # document (e.g. boilerplate header words like "policy"/"support"/
    # "customer") must not retrieve anything, exactly like a query built from
    # words absent from the corpus entirely. This must hold for whatever
    # words happen to be common in a given corpus, not a hand-picked list.
    # MockEmbeddingAdapter applies the same zero-floored raw-IDF weighting
    # (app.retrieval.lexical.raw_idf) to near-universal terms, so this test
    # now also proves the embedding branch doesn't leak a false-positive
    # "closest" doc for a query with no genuine signal in either branch.
    for name, unique_word in [
        ("a", "alpha"), ("b", "bravo"), ("c", "charlie"), ("d", "delta"),
    ]:
        (tmp_path / f"{name}.md").write_text(
            f"# Doc {name}\n\nDoc ID: KB-{name.upper()}-001\n\n"
            f"This is a customer support policy about {unique_word}.",
            encoding="utf-8",
        )

    index = KBIndex(MockEmbeddingAdapter())
    index.load_directory(str(tmp_path))

    # every document shares "customer support policy about" -- a query using
    # only those words must return nothing, not one arbitrary "least common"
    # document via the epsilon floor.
    assert index.search("customer support policy about") == []

    # a query for a term genuinely unique to one document still works.
    results = index.search("alpha")
    assert len(results) == 1
    assert results[0].doc_id == "KB-A-001"


def test_ingest_adds_new_document_and_makes_it_searchable():
    index = KBIndex(MockEmbeddingAdapter())

    ids = index.ingest([
        DocumentIn(
            doc_id="KB-TEST-001",
            title="Test Doc",
            content="This document is about a cracked earbud replacement request.",
            source_path="test.md",
        ),
        DocumentIn(
            doc_id="KB-TEST-002",
            title="Unrelated Doc A",
            content="This document discusses quarterly financial reporting procedures.",
            source_path="test2.md",
        ),
        DocumentIn(
            doc_id="KB-TEST-003",
            title="Unrelated Doc B",
            content="This document explains annual leave policy for employees.",
            source_path="test3.md",
        ),
    ])

    assert ids == ["KB-TEST-001", "KB-TEST-002", "KB-TEST-003"]
    results = index.search("cracked earbud")
    assert len(results) == 1
    assert results[0].doc_id == "KB-TEST-001"


def test_rrf_fuse_combines_ranks_from_both_lists():
    bm25_ranked = ["KB-X-001", "KB-Y-001"]
    embedding_ranked = ["KB-Y-001", "KB-X-001", "KB-Z-001"]

    scores = _rrf_fuse(bm25_ranked, embedding_ranked)

    assert scores["KB-X-001"] == pytest.approx(1 / 61 + 1 / 62)
    assert scores["KB-Y-001"] == pytest.approx(1 / 62 + 1 / 61)
    assert scores["KB-Z-001"] == pytest.approx(1 / 63)
    assert scores["KB-X-001"] == pytest.approx(scores["KB-Y-001"])


def test_embedding_branch_surfaces_a_match_bm25_misses():
    class _FixedVectorEmbeddingAdapter:
        def __init__(self, vectors: dict[str, list[float]]):
            self._vectors = vectors

        def embed_documents(self, texts: list[str]) -> list[list[float]]:
            return [self._vectors[text] for text in texts]

        def embed_query(self, text: str) -> list[float]:
            return self._vectors[text]

    doc_a_content = "Large mammals graze together across savanna landscapes."
    doc_b_content = "Quarterly financial reporting procedures for vendors."
    query = "striped equine herd behavior"

    adapter = _FixedVectorEmbeddingAdapter({
        doc_a_content: [1.0, 0.0],
        doc_b_content: [0.0, 1.0],
        query: [1.0, 0.0],
    })

    index = KBIndex(adapter)
    index.ingest([
        DocumentIn(doc_id="KB-A-001", title="Doc A", content=doc_a_content, source_path="a.md"),
        DocumentIn(doc_id="KB-B-001", title="Doc B", content=doc_b_content, source_path="b.md"),
    ])

    results = index.search(query)

    assert [r.doc_id for r in results] == ["KB-A-001"]
    assert results[0].score > 0
```

- [ ] **Step 3: Run test to verify it fails**

Run: `pytest tests/test_kb_index.py -v`
Expected: FAIL — `TypeError: KBIndex.__init__() missing 1 required positional argument: 'embedding_adapter'` (and `ImportError` for `_rrf_fuse`, which doesn't exist yet either).

- [ ] **Step 4: Implement the hybrid `KBIndex`**

Replace the full contents of `ai-service/app/retrieval/kb_index.py` with:

```python
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import chromadb
from rank_bm25 import BM25Okapi

from app.adapters.embedding_adapter import EmbeddingAdapter
from app.retrieval.lexical import raw_idf, tokenize
from app.schemas.documents import DocumentIn, SearchResult

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
        embeddings = self._embedding_adapter.embed_documents(contents)
        client = chromadb.EphemeralClient()
        collection = client.get_or_create_collection(
            name="kb_docs", metadata={"hnsw:space": "cosine"},
        )
        collection.add(ids=self._doc_order, embeddings=embeddings, documents=contents)
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
```

- [ ] **Step 5: Run `test_kb_index.py` to verify it passes**

Run: `pytest tests/test_kb_index.py -v`
Expected: PASS (7 tests)

- [ ] **Step 6: Update `app/routers/documents.py`**

Replace the top of `ai-service/app/routers/documents.py` (imports through the `kb_index.load_directory(...)` line) with:

```python
from fastapi import APIRouter, Depends

from app.adapters import get_embedding_adapter
from app.retrieval.kb_index import KBIndex
from app.schemas.documents import IngestRequest, IngestResponse, SearchResponse
from app.security import verify_internal_key
from app.settings import settings

router = APIRouter(dependencies=[Depends(verify_internal_key)])
kb_index = KBIndex(get_embedding_adapter(), similarity_threshold=settings.embedding_similarity_threshold)
kb_index.load_directory(f"{settings.data_dir}/knowledge_base")
```

(The two route functions below stay unchanged.)

- [ ] **Step 7: Update `app/eval/eval_runner.py`**

In `ai-service/app/eval/eval_runner.py`, change the import line:

```python
from app.adapters import get_model_adapter
```

to:

```python
from app.adapters import get_embedding_adapter, get_model_adapter
```

And change:

```python
    if kb_index_instance is None:
        kb_index_instance = KBIndex()
        kb_index_instance.load_directory(f"{settings.data_dir}/knowledge_base")
```

to:

```python
    if kb_index_instance is None:
        kb_index_instance = KBIndex(
            get_embedding_adapter(), similarity_threshold=settings.embedding_similarity_threshold,
        )
        kb_index_instance.load_directory(f"{settings.data_dir}/knowledge_base")
```

- [ ] **Step 8: Update `tests/test_draft_graph.py`**

In `ai-service/tests/test_draft_graph.py`, add an import and update `_real_kb_index()`:

```python
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter
from app.graphs.draft_graph import build_draft_graph
from app.retrieval.kb_index import KBIndex


def _real_kb_index() -> KBIndex:
    index = KBIndex(MockEmbeddingAdapter())
    index.load_directory("../data/knowledge_base")
    return index
```

(Everything below this helper stays unchanged.)

- [ ] **Step 9: Update `tests/test_eval_runner.py`**

In `ai-service/tests/test_eval_runner.py`, add an import and update `_real_kb_index()`:

```python
from app.adapters.mock_adapter import MockModelAdapter
from app.adapters.mock_embedding_adapter import MockEmbeddingAdapter
from app.eval.eval_runner import run_eval
from app.retrieval.kb_index import KBIndex


def _real_kb_index() -> KBIndex:
    index = KBIndex(MockEmbeddingAdapter())
    index.load_directory("../data/knowledge_base")
    return index
```

(Everything below this helper stays unchanged.)

- [ ] **Step 10: Run the full test suite**

Run (from `ai-service/`): `pytest -v`
Expected: PASS — every test in the suite, including the adversarial/off-topic/citation regression tests in `test_draft_graph.py` and `test_documents_router.py`. If any of those fail, the fix is in the hybrid search logic (Step 4), not in the test — do not weaken or delete a regression test to make it pass.

- [ ] **Step 11: Commit**

```bash
git add ai-service/requirements.txt ai-service/app/retrieval/kb_index.py ai-service/app/routers/documents.py ai-service/app/eval/eval_runner.py ai-service/tests/test_kb_index.py ai-service/tests/test_draft_graph.py ai-service/tests/test_eval_runner.py
git commit -m "feat: fuse BM25 and embedding search in KBIndex via Reciprocal Rank Fusion"
```
