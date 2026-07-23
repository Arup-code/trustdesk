from fastapi import APIRouter, Depends

from app.adapters import get_model_adapter
from app.graphs.draft_graph import build_draft_graph
from app.graphs.triage_graph import build_triage_graph
from app.routers import documents
from app.schemas.draft import DraftRequest, DraftResponse
from app.schemas.triage import TriageRequest, TriageResponse
from app.security import verify_internal_key

router = APIRouter(prefix="/internal", dependencies=[Depends(verify_internal_key)])
_triage_graph = build_triage_graph(get_model_adapter())
_draft_graph = build_draft_graph(get_model_adapter(), documents.kb_index)


@router.post("/triage", response_model=TriageResponse)
def triage(request: TriageRequest) -> TriageResponse:
    result = _triage_graph.invoke({
        "ticket_text": f"{request.subject}\n{request.body}",
        "context": {"customer": request.customer, "order": request.order},
    })
    return TriageResponse(
        category=result["category"],
        priority=result["priority"],
        sentiment=result["sentiment"],
        should_escalate=result["should_escalate"],
        reason_summary=result["reason_summary"],
        guardrail_flagged=result.get("guardrail_flagged", False),
        guardrail_category=result.get("guardrail_category"),
    )


@router.post("/draft", response_model=DraftResponse)
def draft(request: DraftRequest) -> DraftResponse:
    result = _draft_graph.invoke({
        "ticket_text": f"{request.subject}\n{request.body}",
        "context": {"customer": request.customer, "order": request.order},
        "category": request.category,
    })
    return DraftResponse(
        body=result.get("body", ""),
        citations=result.get("citations", []),
        recommended_actions=result.get("recommended_actions", []),
        status=result.get("status", "generated"),
        retrieved_doc_ids=result.get("retrieved_doc_ids", []),
        guardrail_flagged=result.get("guardrail_flagged", False),
        guardrail_category=result.get("guardrail_category"),
    )
