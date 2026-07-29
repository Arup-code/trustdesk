import { useState } from "react";
import "./App.css";
import { getToken, clearToken } from "./api/client";
import { Login } from "./pages/Login";
import { TicketQueue } from "./pages/TicketQueue";
import { TicketDetail } from "./pages/TicketDetail";
import { EvalSummary } from "./pages/EvalSummary";

type View = { name: "queue" } | { name: "detail"; ticketId: string } | { name: "evals" };

function App() {
  const [loggedIn, setLoggedIn] = useState(() => Boolean(getToken()));
  const [view, setView] = useState<View>({ name: "queue" });

  if (!loggedIn) {
    return <Login onLoggedIn={() => setLoggedIn(true)} />;
  }

  return (
    <div className="app">
      <nav>
        <button onClick={() => setView({ name: "queue" })}>Tickets</button>
        <button onClick={() => setView({ name: "evals" })}>Evals</button>
        <button
          onClick={() => {
            clearToken();
            setLoggedIn(false);
          }}
        >
          Log out
        </button>
      </nav>
      {view.name === "queue" && (
        <TicketQueue onOpenTicket={(ticketId) => setView({ name: "detail", ticketId })} />
      )}
      {view.name === "detail" && (
        <TicketDetail ticketId={view.ticketId} onBack={() => setView({ name: "queue" })} />
      )}
      {view.name === "evals" && <EvalSummary />}
    </div>
  );
}

export default App;
