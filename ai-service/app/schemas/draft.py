from pydantic import BaseModel, Field


class DraftRequest(BaseModel):
    ticket_id: str
    subject: str
    body: str
    category: str | None = None
    customer: dict = Field(default_factory=dict)
    order: dict = Field(default_factory=dict)


class RecommendedAction(BaseModel):
    tool_name: str
    requires_human_approval: bool
    reason: str


class DraftResponse(BaseModel):
    body: str
    citations: list[str] = Field(default_factory=list)
    recommended_actions: list[RecommendedAction] = Field(default_factory=list)
    status: str
    retrieved_doc_ids: list[str] = Field(default_factory=list)
    guardrail_flagged: bool = False
    guardrail_category: str | None = None
