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
