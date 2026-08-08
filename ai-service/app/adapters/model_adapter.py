from typing import Protocol


class ModelAdapter(Protocol):
    def generate(self, prompt: str) -> str: ...

    def classify(self, ticket_text: str, context: dict) -> dict: ...
