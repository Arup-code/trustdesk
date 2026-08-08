from app.graphs.tool_recommendation import recommend_tool


def test_recommends_replacement_for_damaged_refund_item():
    result = recommend_tool("refund", "My earbuds arrived damaged, can I get a replacement?")
    assert result == {
        "tool_name": "create_replacement_order",
        "requires_human_approval": True,
        "reason": "Damaged or defective item reported within policy window.",
    }


def test_recommends_replacement_for_defective_warranty_item():
    result = recommend_tool("warranty", "This is defective, it stopped working.")
    assert result["tool_name"] == "create_replacement_order"


def test_does_not_recommend_for_final_sale_items():
    result = recommend_tool("refund", "I want to refund my software license, it's final sale.")
    assert result is None


def test_does_not_recommend_for_unrelated_category():
    result = recommend_tool("shipping", "My package has damaged tracking info, not moved in days.")
    assert result is None
