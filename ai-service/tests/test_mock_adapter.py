from app.adapters.mock_adapter import MockModelAdapter


def test_mock_adapter_has_generate_and_classify():
    adapter = MockModelAdapter()
    assert hasattr(adapter, "generate")
    assert hasattr(adapter, "classify")


def test_classify_shipping_ticket():
    adapter = MockModelAdapter()
    result = adapter.classify("Tracking has not moved for 6 business days.", {})
    assert result["category"] == "shipping"


def test_classify_refund_ticket():
    adapter = MockModelAdapter()
    result = adapter.classify(
        "My BlueBuds Air arrived with the left earbud cracked. Can I get a replacement?", {})
    assert result["category"] == "refund"


def test_classify_unmatched_ticket_defaults_to_general():
    adapter = MockModelAdapter()
    result = adapter.classify("Hello, just saying thanks for the great service!", {})
    assert result["category"] == "general"


def test_generate_returns_nonempty_string():
    adapter = MockModelAdapter()
    assert isinstance(adapter.generate("any prompt"), str)
    assert len(adapter.generate("any prompt")) > 0


def test_generate_echoes_doc_ids_found_in_prompt_as_citations():
    adapter = MockModelAdapter()
    prompt = "REFERENCE MATERIAL:\n[KB-REFUND-001] Refund Policy: some content here.\n\nWrite a reply."
    response = adapter.generate(prompt)
    assert "[KB-REFUND-001]" in response


def test_generate_without_doc_ids_in_prompt_has_no_citation_markers():
    adapter = MockModelAdapter()
    response = adapter.generate("Write a generic reply with no reference material.")
    assert "[KB-" not in response
