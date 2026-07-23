from langgraph.graph import END, StateGraph

from app.adapters.model_adapter import ModelAdapter
from app.graphs.state import TriageState
from app.guardrails.precheck import precheck


def build_triage_graph(model_adapter: ModelAdapter):
    def guardrail_precheck_node(state: TriageState) -> dict:
        result = precheck(state["ticket_text"])
        return {"guardrail_flagged": result.flagged, "guardrail_category": result.category}

    def classify_node(state: TriageState) -> dict:
        classification = model_adapter.classify(state["ticket_text"], state.get("context", {}))
        return {
            "category": classification["category"],
            "priority": classification["priority"],
            "sentiment": classification["sentiment"],
            "reason_summary": classification["reason_summary"],
        }

    def finalize_node(state: TriageState) -> dict:
        if state.get("guardrail_flagged"):
            return {
                "category": state.get("category", "account_security"),
                "priority": state.get("priority", "high"),
                "sentiment": state.get("sentiment", "neutral"),
                "should_escalate": True,
                "reason_summary": f"Guardrail flagged: {state.get('guardrail_category')} pattern detected.",
            }
        return {"should_escalate": False}

    def route_after_guardrail(state: TriageState) -> str:
        return "finalize" if state.get("guardrail_flagged") else "classify"

    graph = StateGraph(TriageState)
    graph.add_node("guardrail_precheck", guardrail_precheck_node)
    graph.add_node("classify", classify_node)
    graph.add_node("finalize", finalize_node)

    graph.set_entry_point("guardrail_precheck")
    graph.add_conditional_edges(
        "guardrail_precheck", route_after_guardrail, {"finalize": "finalize", "classify": "classify"})
    graph.add_edge("classify", "finalize")
    graph.add_edge("finalize", END)

    return graph.compile()
