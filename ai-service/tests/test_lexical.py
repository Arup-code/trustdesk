from app.retrieval.lexical import raw_idf, tokenize


def test_tokenize_lowercases_and_strips_stopwords():
    assert tokenize("The Damaged Item Was Reported") == ["damaged", "item", "reported"]


def test_tokenize_extracts_alphanumeric_tokens_only():
    assert tokenize("order #12345, refund!") == ["order", "12345", "refund"]


def test_raw_idf_is_negative_for_a_term_in_more_than_half_the_corpus():
    assert raw_idf(doc_freq=3, doc_count=4) < 0


def test_raw_idf_is_positive_for_a_rare_term():
    assert raw_idf(doc_freq=1, doc_count=4) > 0
