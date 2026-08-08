import json

from langchain_openai import ChatOpenAI

from app.settings import settings


class OpenRouterAdapter:
    def __init__(self) -> None:
        if not settings.openrouter_api_key:
            raise ValueError("OPENROUTER_API_KEY is required when AI_MODEL_MODE=openrouter")
        self._llm = ChatOpenAI(
            base_url="https://openrouter.ai/api/v1",
            api_key=settings.openrouter_api_key,
            model=settings.openrouter_model,
        )
        # Classification is a structured judgment call (category/priority/should_escalate),
        # not creative prose -- the default sampling temperature made it visibly
        # non-deterministic (the same ticket text classified differently across
        # back-to-back calls), which shows up as flaky eval metrics for no code
        # reason. A separate near-zero-temperature client keeps generate()'s
        # customer-facing prose at the default temperature while making classify()
        # consistent.
        self._classify_llm = ChatOpenAI(
            base_url="https://openrouter.ai/api/v1",
            api_key=settings.openrouter_api_key,
            model=settings.openrouter_model,
            temperature=0,
        )

    def generate(self, prompt: str) -> str:
        response = self._llm.invoke(prompt)
        return str(response.content)

    def classify(self, ticket_text: str, context: dict) -> dict:
        prompt = (
            "Classify this support ticket. Respond with ONLY a JSON object with keys "
            "category (one of: shipping, refund, warranty, billing, account_security, general -- "
            "use refund for a damaged/defective item still within the return window or a request "
            "for money back, and warranty for a product defect being claimed under the "
            "manufacturer's warranty rather than a return), "
            "priority (one of: low, medium, high, urgent -- reflects business urgency, not the "
            "customer's tone: a routine policy question with a clear, unfavorable answer -- e.g. "
            "a final-sale item that isn't refundable -- is low or medium priority even if the "
            "customer is unhappy about it; reserve urgent for safety hazards or issues needing "
            "immediate action), "
            "sentiment (one of: neutral, frustrated, angry, negative, positive, confused, concerned, satisfied), "
            "should_escalate (true or false -- true only for safety hazards (e.g. a swelling or "
            "overheating battery) or requests you cannot resolve within policy; false otherwise), "
            "reason_summary (one short sentence).\n\n"
            f"Ticket: {ticket_text}\nContext: {context}"
        )
        response = self._classify_llm.invoke(prompt)
        return json.loads(str(response.content))
