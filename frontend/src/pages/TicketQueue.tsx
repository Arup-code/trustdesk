import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
import type { Ticket } from "../api/types";

export function TicketQueue({ onOpenTicket }: { onOpenTicket: (ticketId: string) => void }) {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<Ticket[]>("/tickets")
      .then(setTickets)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load tickets"));
  }, []);

  return (
    <div className="page">
      <h2>Ticket Queue</h2>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Ticket ID</th>
            <th>Subject</th>
            <th>Category</th>
            <th>Priority</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr key={ticket.ticketId} onClick={() => onOpenTicket(ticket.ticketId)} className="clickable-row">
              <td>{ticket.ticketId}</td>
              <td>{ticket.subject}</td>
              <td>{ticket.category ?? "—"}</td>
              <td>{ticket.priority ?? "—"}</td>
              <td>{ticket.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
