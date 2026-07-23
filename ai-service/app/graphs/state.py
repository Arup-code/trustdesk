from typing import Any, TypedDict


class TriageState(TypedDict, total=False):
    ticket_text: str
    context: dict[str, Any]
    guardrail_flagged: bool
    guardrail_category: str | None
    category: str
    priority: str
    sentiment: str
    should_escalate: bool
    reason_summary: str
