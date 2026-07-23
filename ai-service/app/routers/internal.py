from fastapi import APIRouter, Depends

from app.adapters import get_model_adapter
from app.graphs.triage_graph import build_triage_graph
from app.schemas.triage import TriageRequest, TriageResponse
from app.security import verify_internal_key

router = APIRouter(prefix="/internal", dependencies=[Depends(verify_internal_key)])
_triage_graph = build_triage_graph(get_model_adapter())


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
