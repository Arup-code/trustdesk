from pydantic import BaseModel, Field


class TriageRequest(BaseModel):
    ticket_id: str
    subject: str
    body: str
    customer: dict = Field(default_factory=dict)
    order: dict = Field(default_factory=dict)


class TriageResponse(BaseModel):
    category: str
    priority: str
    sentiment: str
    should_escalate: bool
    reason_summary: str
    guardrail_flagged: bool = False
    guardrail_category: str | None = None
    run_type: str = "triage"
