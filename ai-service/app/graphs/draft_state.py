from typing import Any, TypedDict


class DraftState(TypedDict, total=False):
    ticket_text: str
    context: dict[str, Any]
    category: str | None
    guardrail_flagged: bool
    guardrail_category: str | None
    retrieved_doc_ids: list[str]
    retrieved_snippets: list[dict]
    body: str
    citations: list[str]
    recommended_actions: list[dict]
    status: str
