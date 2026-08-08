import { useEffect, useState } from "react";
import { apiFetch, getUsername } from "../api/client";
import type { DraftResponse, TicketDetail as TicketDetailType, ToolActionRequest, TriageResponse } from "../api/types";

export function TicketDetail({ ticketId, onBack }: { ticketId: string; onBack: () => void }) {
  const [ticket, setTicket] = useState<TicketDetailType | null>(null);
  const [triage, setTriage] = useState<TriageResponse | null>(null);
  const [draft, setDraft] = useState<DraftResponse | null>(null);
  const [pendingActions, setPendingActions] = useState<ToolActionRequest[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function loadTicket() {
    apiFetch<TicketDetailType>(`/tickets/${ticketId}`).then(setTicket).catch(reportError);
  }

  function loadPendingActions() {
    apiFetch<ToolActionRequest[]>(`/tool-actions?ticket_id=${ticketId}`)
      .then(setPendingActions)
      .catch(reportError);
  }

  useEffect(() => {
    loadTicket();
    loadPendingActions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId]);

  function reportError(err: unknown) {
    setError(err instanceof Error ? err.message : "Something went wrong");
  }

  async function runTriage() {
    setBusy(true);
    setError(null);
    try {
      const result = await apiFetch<TriageResponse>(`/tickets/${ticketId}/triage`, { method: "POST" });
      setTriage(result);
      loadTicket();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  async function generateDraft() {
    setBusy(true);
    setError(null);
    try {
      const result = await apiFetch<DraftResponse>(`/tickets/${ticketId}/draft-reply`, { method: "POST" });
      setDraft(result);
      loadPendingActions();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  async function approveAction(actionId: string, decision: "approved" | "rejected") {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/tool-actions/${actionId}/approve`, {
        method: "POST",
        body: JSON.stringify({ reviewer_id: getUsername() ?? "unknown", decision, reason: "Reviewed via UI" }),
      });
      loadPendingActions();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  async function executeAction(actionId: string) {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/tool-actions/${actionId}/execute`, { method: "POST" });
      loadPendingActions();
    } catch (err) {
      reportError(err);
    } finally {
      setBusy(false);
    }
  }

  if (!ticket) {
    return (
      <div className="page">
        <button onClick={onBack}>Back to queue</button>
        <p>Loading...</p>
      </div>
    );
  }

  return (
    <div className="page">
      <button onClick={onBack}>Back to queue</button>
      <h2>{ticket.subject}</h2>
      <p>{ticket.body}</p>
      {ticket.customer && (
        <p className="context">
          Customer: {ticket.customer.name} ({ticket.customer.tier}, {ticket.customer.country})
        </p>
      )}
      {ticket.order && (
        <p className="context">
          Order: {ticket.order.orderId} — {ticket.order.status}
        </p>
      )}
      <p>
        Category: {ticket.category ?? "not yet triaged"} | Priority: {ticket.priority ?? "—"} | Escalate:{" "}
        {String(ticket.shouldEscalate ?? "—")}
      </p>

      {error && <p className="error">{error}</p>}

      <div className="actions">
        <button onClick={runTriage} disabled={busy}>Run Triage</button>
        <button onClick={generateDraft} disabled={busy}>Generate Draft</button>
      </div>

      {triage && (
        <section>
          <h3>Triage Result</h3>
          <p>{triage.reason_summary}</p>
        </section>
      )}

      {draft && (
        <section>
          <h3>Draft Reply</h3>
          <p>{draft.body}</p>
          <div className="chips">
            {draft.citations.map((doc) => (
              <span key={doc} className="chip">{doc}</span>
            ))}
          </div>
          <p>Status: {draft.status}</p>
        </section>
      )}

      {pendingActions.length > 0 && (
        <section>
          <h3>Pending Tool Actions</h3>
          <ul>
            {pendingActions.map((action) => (
              <li key={action.actionId}>
                {action.toolName} — {action.status}
                {action.status === "approval_required" && (
                  <>
                    <button onClick={() => approveAction(action.actionId, "approved")} disabled={busy}>Approve</button>
                    <button onClick={() => approveAction(action.actionId, "rejected")} disabled={busy}>Reject</button>
                  </>
                )}
                {action.status === "approved" && (
                  <button onClick={() => executeAction(action.actionId)} disabled={busy}>Execute</button>
                )}
                {action.status === "executed" && action.result && (
                  <span className="result">{JSON.stringify(action.result)}</span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
