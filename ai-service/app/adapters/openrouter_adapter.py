import json

from langchain_openai import ChatOpenAI

from app.settings import settings


class OpenRouterAdapter:
    def __init__(self) -> None:
        self._llm = ChatOpenAI(
            base_url="https://openrouter.ai/api/v1",
            api_key=settings.openrouter_api_key,
            model=settings.openrouter_model,
        )

    def generate(self, prompt: str) -> str:
        response = self._llm.invoke(prompt)
        return str(response.content)

    def classify(self, ticket_text: str, context: dict) -> dict:
        prompt = (
            "Classify this support ticket. Respond with ONLY a JSON object with keys "
            "category (one of: shipping, refund, warranty, billing, account_security, general), "
            "priority (one of: low, medium, high, urgent), "
            "sentiment (one of: neutral, frustrated, angry, negative, positive, confused, concerned, satisfied), "
            "reason_summary (one short sentence).\n\n"
            f"Ticket: {ticket_text}\nContext: {context}"
        )
        response = self._llm.invoke(prompt)
        return json.loads(str(response.content))
