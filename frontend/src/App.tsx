import { useState } from "react";
import "./App.css";
import { getToken, clearToken, clearUsername, resetDemoData } from "./api/client";
import { Login } from "./pages/Login";
import { TicketQueue } from "./pages/TicketQueue";
import { TicketDetail } from "./pages/TicketDetail";
import { EvalSummary } from "./pages/EvalSummary";

type View = { name: "queue" } | { name: "detail"; ticketId: string } | { name: "evals" };

function App() {
  const [loggedIn, setLoggedIn] = useState(() => Boolean(getToken()));
  const [view, setView] = useState<View>({ name: "queue" });
  const [resetting, setResetting] = useState(false);
  const [resetGeneration, setResetGeneration] = useState(0);

  if (!loggedIn) {
    return <Login onLoggedIn={() => setLoggedIn(true)} />;
  }

  async function handleResetDemo() {
    if (!confirm("This clears all triage results, draft replies, tool actions, approvals, and eval history. Continue?")) {
      return;
    }
    setResetting(true);
    try {
      await resetDemoData();
      setResetGeneration((n) => n + 1);
      setView({ name: "queue" });
    } catch (err) {
      alert(err instanceof Error ? err.message : "Reset failed");
    } finally {
      setResetting(false);
    }
  }

  return (
    <div className="app">
      <nav>
        <button onClick={() => setView({ name: "queue" })}>Tickets</button>
        <button onClick={() => setView({ name: "evals" })}>Evals</button>
        <button onClick={handleResetDemo} disabled={resetting}>
          {resetting ? "Resetting..." : "Reset Demo Data"}
        </button>
        <button
          onClick={() => {
            clearToken();
            clearUsername();
            setLoggedIn(false);
          }}
        >
          Log out
        </button>
      </nav>
      {view.name === "queue" && (
        <TicketQueue
          key={resetGeneration}
          onOpenTicket={(ticketId) => setView({ name: "detail", ticketId })}
        />
      )}
      {view.name === "detail" && (
        <TicketDetail ticketId={view.ticketId} onBack={() => setView({ name: "queue" })} />
      )}
      {view.name === "evals" && <EvalSummary key={resetGeneration} />}
    </div>
  );
}

export default App;
