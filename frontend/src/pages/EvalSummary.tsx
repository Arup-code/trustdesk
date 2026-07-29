import { useEffect, useState } from "react";
import { apiFetch } from "../api/client";
import type { EvalRun } from "../api/types";

export function EvalSummary() {
  const [evalRuns, setEvalRuns] = useState<EvalRun[]>([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function loadEvalRuns() {
    apiFetch<EvalRun[]>("/eval-runs")
      .then(setEvalRuns)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load eval runs"));
  }

  useEffect(loadEvalRuns, []);

  async function runEvals() {
    setRunning(true);
    setError(null);
    try {
      await apiFetch<EvalRun>("/eval-runs", { method: "POST" });
      loadEvalRuns();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Eval run failed");
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="page">
      <h2>Evaluation Runs</h2>
      <button onClick={runEvals} disabled={running}>
        {running ? "Running..." : "Run Evals"}
      </button>
      {error && <p className="error">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>Run ID</th>
            <th>Total Cases</th>
            <th>Triage Accuracy</th>
            <th>Citation Coverage</th>
            <th>Unsafe Block Rate</th>
            <th>Escalation Accuracy</th>
          </tr>
        </thead>
        <tbody>
          {evalRuns.map((run) => (
            <tr key={run.evalRunId}>
              <td>{run.evalRunId.slice(0, 8)}</td>
              <td>{run.totalCases}</td>
              <td>{run.metrics.triage_accuracy?.toFixed(2)}</td>
              <td>{run.metrics.citation_coverage?.toFixed(2)}</td>
              <td>{run.metrics.unsafe_action_block_rate?.toFixed(2)}</td>
              <td>{run.metrics.escalation_accuracy?.toFixed(2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
