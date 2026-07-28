import json
from typing import Callable

import httpx

from app.adapters import get_model_adapter
from app.graphs.draft_graph import build_draft_graph
from app.graphs.triage_graph import build_triage_graph
from app.retrieval.kb_index import KBIndex
from app.settings import settings


def _load_eval_cases(path: str) -> list[dict]:
    cases = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                cases.append(json.loads(line))
    return cases


def _login() -> str:
    response = httpx.post(
        f"{settings.java_base_url}/auth/login",
        json={"username": settings.eval_java_username, "password": settings.eval_java_password},
        timeout=10.0,
    )
    response.raise_for_status()
    return response.json()["token"]


def _default_fetch_ticket_factory() -> Callable[[str], dict]:
    token = _login()

    def fetch(ticket_id: str) -> dict:
        response = httpx.get(
            f"{settings.java_base_url}/tickets/{ticket_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10.0,
        )
        response.raise_for_status()
        return response.json()

    return fetch


def run_eval(
    cases_path: str | None = None,
    model_adapter=None,
    kb_index_instance: KBIndex | None = None,
    fetch_ticket: Callable[[str], dict] | None = None,
) -> dict:
    """Score the real triage/draft LangGraph pipeline against labeled eval cases.

    Each case in the JSONL file at `cases_path` (default: `<data_dir>/eval_cases.jsonl`)
    references a `ticket_id`; `fetch_ticket` resolves that ID to the ticket payload
    (subject/body/customer/order) the graphs need. In production this hits the Java
    Core Service over HTTP (logging in with the eval service account first); tests
    inject a stub dict lookup instead so they never need a running Java process.
    """
    cases_path = cases_path or f"{settings.data_dir}/eval_cases.jsonl"
    cases = _load_eval_cases(cases_path)

    model_adapter = model_adapter or get_model_adapter()
    if kb_index_instance is None:
        kb_index_instance = KBIndex()
        kb_index_instance.load_directory(f"{settings.data_dir}/knowledge_base")
    fetch_ticket = fetch_ticket or _default_fetch_ticket_factory()

    triage_graph = build_triage_graph(model_adapter)
    draft_graph = build_draft_graph(model_adapter, kb_index_instance)

    case_results = []
    for case in cases:
        expected = case["expected"]
        ticket = fetch_ticket(case["ticket_id"])
        ticket_text = f"{ticket['subject']}\n{ticket['body']}"
        context = {"customer": ticket.get("customer") or {}, "order": ticket.get("order") or {}}

        triage_result = triage_graph.invoke({"ticket_text": ticket_text, "context": context})
        draft_result = draft_graph.invoke({
            "ticket_text": ticket_text,
            "context": context,
            "category": triage_result.get("category"),
        })

        category_match = triage_result.get("category") == expected.get("category")
        priority_match = triage_result.get("priority") == expected.get("priority")
        must_cite = set(expected.get("must_cite_doc_ids", []))
        citations = set(draft_result.get("citations", []))
        citation_ok = must_cite.issubset(citations)
        disallowed = set(expected.get("disallowed_actions", []))
        recommended = {a["tool_name"] for a in draft_result.get("recommended_actions", [])}
        unsafe_ok = disallowed.isdisjoint(recommended)
        escalation_match = triage_result.get("should_escalate") == expected.get("should_escalate")

        case_results.append({
            "case_id": case["case_id"],
            "ticket_id": case["ticket_id"],
            "category_match": category_match,
            "priority_match": priority_match,
            "citation_ok": citation_ok,
            "unsafe_ok": unsafe_ok,
            "escalation_match": escalation_match,
            "passed": category_match and priority_match and citation_ok
                      and unsafe_ok and escalation_match,
        })

    total = len(case_results)
    metrics = {
        "triage_accuracy": sum(c["category_match"] for c in case_results) / total,
        "priority_accuracy": sum(c["priority_match"] for c in case_results) / total,
        "citation_coverage": sum(c["citation_ok"] for c in case_results) / total,
        "unsafe_action_block_rate": sum(c["unsafe_ok"] for c in case_results) / total,
        "escalation_accuracy": sum(c["escalation_match"] for c in case_results) / total,
    }

    return {"total_cases": total, "metrics": metrics, "case_results": case_results}
