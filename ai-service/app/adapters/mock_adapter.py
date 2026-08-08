import re

_DOC_ID_IN_PROMPT = re.compile(r"\bKB-[A-Z0-9-]+\b")

_CATEGORY_KEYWORDS = {
    "refund": ["refund", "return", "damaged", "replacement", "defective", "cracked"],
    "shipping": ["tracking", "shipment", "delivery", "package", "carrier", "not moved"],
    "warranty": ["warranty", "battery", "swelling", "malfunction"],
    "billing": ["charge", "invoice", "payment", "double charge", "billed"],
    "account_security": ["password", "email change", "verify", "identity"],
}

_URGENT_KEYWORDS = ["swelling", "burning", "shock", "fire", "safety"]
_HIGH_KEYWORDS = ["urgent", "asap", "not moved", "double charge"]
_FRUSTRATED_KEYWORDS = ["frustrated", "angry", "unacceptable", "cracked"]


class MockModelAdapter:
    def classify(self, ticket_text: str, context: dict) -> dict:
        text_lower = ticket_text.lower()
        category = "general"
        for cat, keywords in _CATEGORY_KEYWORDS.items():
            if any(kw in text_lower for kw in keywords):
                category = cat
                break

        if any(kw in text_lower for kw in _URGENT_KEYWORDS):
            priority = "urgent"
        elif any(kw in text_lower for kw in _HIGH_KEYWORDS):
            priority = "high"
        else:
            priority = "medium"

        sentiment = "frustrated" if any(kw in text_lower for kw in _FRUSTRATED_KEYWORDS) else "neutral"
        should_escalate = any(kw in text_lower for kw in _URGENT_KEYWORDS)

        return {
            "category": category,
            "priority": priority,
            "sentiment": sentiment,
            "should_escalate": should_escalate,
            "reason_summary": f"Classified as {category} based on keyword match (mock adapter).",
        }

    def generate(self, prompt: str) -> str:
        doc_ids = list(dict.fromkeys(_DOC_ID_IN_PROMPT.findall(prompt)))
        base = "Thank you for reaching out. Based on our policy, here is how we can help."
        if doc_ids:
            citations = " ".join(f"[{doc_id}]" for doc_id in doc_ids)
            return f"{base} {citations} [MOCK RESPONSE]"
        return f"{base} [MOCK RESPONSE]"
