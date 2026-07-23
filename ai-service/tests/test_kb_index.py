from app.retrieval.kb_index import KBIndex


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

    index = KBIndex()
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

    index = KBIndex()
    index.load_directory(str(tmp_path))

    results = index.search("damaged replacement")
    assert all(r.score > 0 for r in results)
    assert "KB-SHIPPING-001" not in [r.doc_id for r in results]
    assert "KB-WARRANTY-001" not in [r.doc_id for r in results]


def test_search_returns_empty_list_for_empty_index():
    index = KBIndex()
    assert index.search("anything") == []


def test_search_ignores_terms_common_to_more_than_half_the_corpus(tmp_path):
    # rank_bm25's BM25Okapi floors any term's negative IDF (a term present in
    # more than half the corpus) to a small *positive* epsilon instead of
    # leaving it negative -- so a query built only from words common to every
    # document (e.g. boilerplate header words like "policy"/"support"/
    # "customer") must not retrieve anything, exactly like a query built from
    # words absent from the corpus entirely. This must hold for whatever
    # words happen to be common in a given corpus, not a hand-picked list.
    for name, unique_word in [
        ("a", "alpha"), ("b", "bravo"), ("c", "charlie"), ("d", "delta"),
    ]:
        (tmp_path / f"{name}.md").write_text(
            f"# Doc {name}\n\nDoc ID: KB-{name.upper()}-001\n\n"
            f"This is a customer support policy about {unique_word}.",
            encoding="utf-8",
        )

    index = KBIndex()
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
    index = KBIndex()
    from app.schemas.documents import DocumentIn

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
