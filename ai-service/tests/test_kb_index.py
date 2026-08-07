import math

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


def test_ingest_twice_does_not_leak_chroma_collections():
    # Regression test for a leak where _rebuild_index() minted a brand-new,
    # never-deleted chromadb collection (name f"kb_docs_{uuid4().hex}") on
    # *every* rebuild instead of reusing one collection per KBIndex instance.
    # A long-lived KBIndex (e.g. the router's module-level singleton, rebuilt
    # on every POST /documents/ingest) would otherwise accumulate one full
    # copy of the corpus's embeddings per ingest call, forever, for the life
    # of the process -- an unbounded, remotely-triggerable memory leak.
    index = KBIndex(MockEmbeddingAdapter())

    index.ingest([
        DocumentIn(
            doc_id="KB-LEAK-001",
            title="First Doc",
            content="This document is about a cracked earbud replacement request.",
            source_path="first.md",
        ),
    ])
    index.ingest([
        DocumentIn(
            doc_id="KB-LEAK-002",
            title="Second Doc",
            content="This document discusses quarterly financial reporting procedures.",
            source_path="second.md",
        ),
    ])

    # chromadb.EphemeralClient() shares one process-wide system across every
    # instance created in this process, so other tests' KBIndex instances
    # legitimately have their own single live collection too -- counting all
    # "kb_docs_*" collections process-wide would conflate that with a leak.
    # What the fix actually guarantees is that *this* index's own
    # never-changing collection name resolves to exactly one live collection
    # after multiple rebuilds, not that it accumulates one per ingest() call.
    # (Also: chromadb 0.6+'s list_collections() returns collection names as
    # strings, not Collection objects -- accessing `.name` on an entry raises
    # NotImplementedError pointing at the v0.6 migration guide.)
    matches = [
        name for name in index._chroma_client.list_collections() if str(name) == index._collection_name
    ]
    assert len(matches) == 1


class _FixedVectorEmbeddingAdapter:
    def __init__(self, vectors: dict[str, list[float]]):
        self._vectors = vectors

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._vectors[text] for text in texts]

    def embed_query(self, text: str) -> list[float]:
        return self._vectors[text]


def test_embedding_branch_surfaces_a_match_bm25_misses():
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


def test_embedding_branch_excludes_result_just_below_similarity_threshold():
    # Pins the default 0.35 similarity_threshold and the >= (inclusive)
    # comparison in KBIndex._embedding_ranked_ids: without this test, someone
    # could change the default threshold or flip >= to > and the full suite
    # would still pass, since the only other embedding-threshold test
    # (test_embedding_branch_surfaces_a_match_bm25_misses) uses orthogonal
    # vectors (similarity 0.0 vs 1.0) -- an infinitely wide margin.
    doc_content = "Large mammals graze together across savanna landscapes."
    query = "striped equine herd behavior"

    doc_vector = [1.0, 0.0]
    similarity = 0.34  # just below the 0.35 default threshold
    theta = math.acos(similarity)
    query_vector = [math.cos(theta), math.sin(theta)]
    assert math.isclose(doc_vector[0] * query_vector[0] + doc_vector[1] * query_vector[1], similarity)

    adapter = _FixedVectorEmbeddingAdapter({doc_content: doc_vector, query: query_vector})
    index = KBIndex(adapter)
    index.ingest([
        DocumentIn(doc_id="KB-THRESH-001", title="Doc", content=doc_content, source_path="thresh.md"),
    ])

    assert index.search(query) == []


class _FailingEmbeddingAdapter:
    """Simulates an embedding provider that is unreachable or misconfigured
    (network outage, invalid/expired API key, rate limit, ...)."""

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        raise RuntimeError("embedding provider unavailable")

    def embed_query(self, text: str) -> list[float]:
        raise RuntimeError("embedding provider unavailable")


def test_load_directory_degrades_to_bm25_only_when_embedding_provider_fails(tmp_path):
    # Regression test: documents.py builds a module-level KBIndex and calls
    # load_directory() at *import* time. If the embedding provider raises
    # (as it does here), that used to propagate all the way out of the
    # module import, crashing the whole ai-service process before it could
    # even start -- taking down completely unrelated routes like
    # /internal/triage (which never touches embeddings) along with it.
    # load_directory() must swallow an embedding-provider failure and
    # degrade to BM25-only search instead of raising.
    # Three documents, not one: with too few documents sharing no query
    # terms, BM25's IDF degenerates to 0 for every term (see the identical
    # note on test_search_ranks_relevant_doc_first above), which would hide
    # a real bug in the degraded-search path behind a BM25 corpus-size
    # artifact instead.
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

    index = KBIndex(_FailingEmbeddingAdapter())
    loaded = index.load_directory(str(tmp_path))  # must not raise

    assert loaded == 3
    results = index.search("damaged replacement")
    assert results[0].doc_id == "KB-REFUND-001"


def test_embedding_branch_includes_result_just_above_similarity_threshold():
    doc_content = "Large mammals graze together across savanna landscapes."
    query = "striped equine herd behavior"

    doc_vector = [1.0, 0.0]
    similarity = 0.36  # just above the 0.35 default threshold
    theta = math.acos(similarity)
    query_vector = [math.cos(theta), math.sin(theta)]
    assert math.isclose(doc_vector[0] * query_vector[0] + doc_vector[1] * query_vector[1], similarity)

    adapter = _FixedVectorEmbeddingAdapter({doc_content: doc_vector, query: query_vector})
    index = KBIndex(adapter, similarity_threshold=0.35)
    index.ingest([
        DocumentIn(doc_id="KB-THRESH-001", title="Doc", content=doc_content, source_path="thresh.md"),
    ])

    results = index.search(query)
    assert [r.doc_id for r in results] == ["KB-THRESH-001"]
