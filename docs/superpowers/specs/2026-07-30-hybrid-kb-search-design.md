# Hybrid BM25 + Embedding Search for ai-service KB Retrieval

## Context

`ai-service`'s knowledge-base retrieval (`app/retrieval/kb_index.py`) currently uses only
`rank_bm25.BM25Okapi` keyword search over an in-memory corpus of 8 markdown KB docs
(`data/knowledge_base/*.md`). It feeds the draft graph's `retrieve_kb_node`
(`app/graphs/draft_graph.py`), which refuses to generate a reply when retrieval returns no
documents (a deliberate guardrail against answering off-topic/adversarial tickets, backed by a
custom IDF-floor fix and dedicated test in `test_kb_index.py`).

There is currently no embedding/vector search anywhere in the service. This spec adds one and
combines it with the existing BM25 search into a hybrid retriever, without weakening the
empty-result refusal guarantee.

## Goals

- Add semantic (embedding-based) retrieval alongside the existing BM25 keyword retrieval.
- Combine both signals into a single ranked result list via Reciprocal Rank Fusion (RRF).
- Preserve the existing contract: if nothing is genuinely relevant, `search()` returns `[]` and
  the draft graph still refuses.
- Follow the codebase's existing mock/real adapter convention (as used for `ModelAdapter`) so
  tests and CI run fully offline by default.

## Non-goals

- No persistence of embeddings across restarts — matches BM25's current behavior (index rebuilt
  in-memory on every load/ingest, corpus is tiny).
- No new ANN infra beyond Chroma's in-memory client — the corpus is 8 documents.
- No change to citation/generation logic downstream of retrieval, beyond the `SearchResult.score`
  meaning changing from "raw BM25 score" to "fused RRF score".

## Design

### 1. `EmbeddingAdapter` protocol

New file `app/adapters/embedding_adapter.py`, mirroring the existing `ModelAdapter` protocol
(`app/adapters/model_adapter.py`):

```python
class EmbeddingAdapter(Protocol):
    def embed_documents(self, texts: list[str]) -> list[list[float]]: ...
    def embed_query(self, text: str) -> list[float]: ...
```

Two implementations:

- **`MockEmbeddingAdapter`** (`app/adapters/mock_embedding_adapter.py`): deterministic, offline,
  no network. Uses a hashing-trick bag-of-words representation (tokenize, hash each token into a
  fixed-dimension vector e.g. 128-d, accumulate, L2-normalize). This gives crude but real
  lexical-overlap-based similarity — good enough to exercise the hybrid pipeline in tests/CI
  without any external dependency, and consistent with `MockModelAdapter`'s
  keyword-based-fake philosophy.
- **`OpenAIEmbeddingAdapter`** (`app/adapters/openai_embedding_adapter.py`): wraps
  `langchain_openai.OpenAIEmbeddings`. **OpenRouter does not proxy embeddings endpoints**, so
  unlike `OpenRouterAdapter` this calls OpenAI directly and needs its own credential. Default
  model `text-embedding-3-small`.

Factory `get_embedding_adapter()` added to `app/adapters/__init__.py`, selecting on a new
`settings.ai_embedding_mode: Literal["mock", "openai"] = "mock"`, same pattern as
`ai_model_mode`.

### 2. New settings (`app/settings.py`)

- `ai_embedding_mode: Literal["mock", "openai"] = "mock"`
- `openai_api_key: str | None = None`
- `embedding_model: str = "text-embedding-3-small"`
- `embedding_similarity_threshold: float = 0.35`

### 3. `KBIndex` changes (`app/retrieval/kb_index.py`)

- Constructor gains a required `embedding_adapter: EmbeddingAdapter` parameter.
- `_rebuild_index` (currently builds the BM25 corpus from `self._documents`) additionally:
  1. Calls `embedding_adapter.embed_documents([doc.content for doc in docs])`.
  2. Loads the vectors into a **fresh in-memory Chroma collection** (ephemeral client, recreated
     every rebuild — same "rebuild from scratch" lifecycle BM25 already has), with `doc_id` as
     the Chroma document id and `hnsw:space="cosine"`.
- `search(query: str, k: int = 5) -> list[SearchResult]`:
  1. **BM25 branch**: unchanged — `BM25Okapi` scores, filter `score <= 0`, keep ranked doc_ids.
  2. **Embedding branch**: `embedding_adapter.embed_query(query)`, query the Chroma collection
     for top-k by cosine similarity, **drop any hit with similarity below
     `settings.embedding_similarity_threshold`**. This threshold is what preserves the
     empty-result refusal contract — nearest-neighbor search always returns *something*, so
     without a floor an off-topic query would always surface a "closest" doc.
  3. **Fusion (RRF)**: for each doc_id appearing in either ranked list, compute
     `score = Σ 1 / (60 + rank)` (1-indexed rank within each list, summed across lists it
     appears in; 60 is the standard RRF constant). Sort descending, take top `k`.
  4. If both branches produced no results after filtering, return `[]` (unchanged behavior).
  5. Build `SearchResult` objects as today (`doc_id`, `title`, 200-char snippet, `score`) — score
     is now the fused RRF score rather than a raw BM25 score.

### 4. Wiring

- `app/main.py` (or wherever `KBIndex` is constructed for the app) passes
  `get_embedding_adapter()` in, same pattern as `get_model_adapter()`.
- Test fixtures / `tests/test_kb_index.py`, `tests/test_documents_router.py`,
  `tests/test_draft_graph.py` construct `KBIndex` with a `MockEmbeddingAdapter()` by default.

### 5. Dependencies (`requirements.txt`)

- Add `chromadb`.
- `langchain-openai` (already present) covers `OpenAIEmbeddings`.

## Testing

Extend `tests/test_kb_index.py`:

- BM25-only match (embedding branch returns nothing above threshold) still ranks correctly.
- Embedding-only match: inject a small fixed-vector fake `EmbeddingAdapter` in the test (not the
  hashing mock) to prove RRF fusion logic deterministically, independent of the mock's actual
  semantics.
- Fully off-topic query (existing near-universal-term test) still returns `[]` through the hybrid
  path, i.e. the embedding similarity floor doesn't leak an irrelevant "closest" doc.
- RRF ordering: construct a case where a doc ranks high in one list and low/absent in the other,
  assert the fused order matches hand-computed RRF scores.
- `ingest()` roundtrip: newly ingested doc is embedded and searchable via the embedding branch
  even when it shares no keywords with the query (would fail with BM25 alone).

Existing `test_documents_router.py` and `test_draft_graph.py` should continue passing unchanged
(interface-compatible), just need `MockEmbeddingAdapter` wired into their `KBIndex` construction.

## Open questions / risks

- `SearchResult.score` scale changes (RRF score, small values ~0.01-0.03) instead of raw BM25
  score. Confirmed no consumer depends on the specific numeric scale (checked
  `test_documents_router.py` — only checks doc presence, not score magnitude).
- Real `OpenAIEmbeddingAdapter` requires a separate `OPENAI_API_KEY` from the OpenRouter one
  used for chat — this is a real infra/cost consideration, not just a code change, when someone
  flips `ai_embedding_mode` to `"openai"` in a real deployment.
