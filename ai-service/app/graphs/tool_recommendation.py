_DAMAGE_KEYWORDS = ["damaged", "defective", "cracked", "broken"]
_FINAL_SALE_KEYWORDS = ["final sale", "final-sale", "license", "non-returnable", "non refundable"]


def recommend_tool(category: str, ticket_text: str) -> dict | None:
    text_lower = ticket_text.lower()
    if category in ("refund", "warranty"):
        if any(kw in text_lower for kw in _FINAL_SALE_KEYWORDS):
            return None
        if any(kw in text_lower for kw in _DAMAGE_KEYWORDS):
            return {
                "tool_name": "create_replacement_order",
                "requires_human_approval": True,
                "reason": "Damaged or defective item reported within policy window.",
            }
    return None
