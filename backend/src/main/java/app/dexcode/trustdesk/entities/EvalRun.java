package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "eval_runs")
public class EvalRun {

    @Id
    @Column(name = "eval_run_id")
    private String evalRunId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_cases")
    private int totalCases;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metrics;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(name = "case_results", columnDefinition = "TEXT")
    private List<Map<String, Object>> caseResults;

    public EvalRun() {}

    public String getEvalRunId() { return evalRunId; }
    public void setEvalRunId(String evalRunId) { this.evalRunId = evalRunId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

    public List<Map<String, Object>> getCaseResults() { return caseResults; }
    public void setCaseResults(List<Map<String, Object>> caseResults) { this.caseResults = caseResults; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EvalRun evalRun = (EvalRun) o;
        return Objects.equals(evalRunId, evalRun.evalRunId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(evalRunId);
    }

    @Override
    public String toString() {
        return "EvalRun{evalRunId='" + evalRunId + "'}";
    }

    public static EvalRunBuilder builder() {
        return new EvalRunBuilder();
    }

    public static class EvalRunBuilder {
        private String evalRunId;
        private Instant startedAt;
        private Instant completedAt;
        private int totalCases;
        private Map<String, Object> metrics;
        private List<Map<String, Object>> caseResults;

        public EvalRunBuilder evalRunId(String evalRunId) { this.evalRunId = evalRunId; return this; }
        public EvalRunBuilder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public EvalRunBuilder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public EvalRunBuilder totalCases(int totalCases) { this.totalCases = totalCases; return this; }
        public EvalRunBuilder metrics(Map<String, Object> metrics) { this.metrics = metrics; return this; }
        public EvalRunBuilder caseResults(List<Map<String, Object>> caseResults) { this.caseResults = caseResults; return this; }

        public EvalRun build() {
            EvalRun e = new EvalRun();
            e.evalRunId = evalRunId;
            e.startedAt = startedAt;
            e.completedAt = completedAt;
            e.totalCases = totalCases;
            e.metrics = metrics;
            e.caseResults = caseResults;
            return e;
        }
    }
}
