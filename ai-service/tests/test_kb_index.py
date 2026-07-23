from app.retrieval.kb_index import KBIndex


def test_search_ranks_relevant_doc_first(tmp_path):
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

    index = KBIndex()
    loaded = index.load_directory(str(tmp_path))

    assert loaded == 2
    results = index.search("damaged item replacement")
    assert results[0].doc_id == "KB-REFUND-001"


def test_search_returns_empty_list_for_empty_index():
    index = KBIndex()
    assert index.search("anything") == []


def test_ingest_adds_new_document_and_makes_it_searchable():
    index = KBIndex()
    from app.schemas.documents import DocumentIn

    ids = index.ingest([DocumentIn(
        doc_id="KB-TEST-001",
        title="Test Doc",
        content="This document is about a cracked earbud replacement request.",
        source_path="test.md",
    )])

    assert ids == ["KB-TEST-001"]
    results = index.search("cracked earbud")
    assert results[0].doc_id == "KB-TEST-001"
