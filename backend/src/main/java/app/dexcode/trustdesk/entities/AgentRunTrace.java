package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "agent_run_traces")
public class AgentRunTrace {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "run_type")
    private String runType;

    private String status;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(name = "retrieved_doc_ids", columnDefinition = "TEXT")
    private List<String> retrievedDocIds;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private List<Map<String, Object>> toolCalls;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(name = "guardrail_results", columnDefinition = "TEXT")
    private Map<String, Object> guardrailResults;

    @Column(name = "created_at")
    private Instant createdAt;

    public AgentRunTrace() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getRetrievedDocIds() { return retrievedDocIds; }
    public void setRetrievedDocIds(List<String> retrievedDocIds) { this.retrievedDocIds = retrievedDocIds; }

    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

    public Map<String, Object> getGuardrailResults() { return guardrailResults; }
    public void setGuardrailResults(Map<String, Object> guardrailResults) { this.guardrailResults = guardrailResults; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRunTrace that = (AgentRunTrace) o;
        return Objects.equals(runId, that.runId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId);
    }

    @Override
    public String toString() {
        return "AgentRunTrace{runId='" + runId + "'}";
    }

    public static AgentRunTraceBuilder builder() {
        return new AgentRunTraceBuilder();
    }

    public static class AgentRunTraceBuilder {
        private String runId;
        private String ticketId;
        private String runType;
        private String status;
        private List<String> retrievedDocIds;
        private List<Map<String, Object>> toolCalls;
        private Map<String, Object> guardrailResults;
        private Instant createdAt;

        public AgentRunTraceBuilder runId(String runId) { this.runId = runId; return this; }
        public AgentRunTraceBuilder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        public AgentRunTraceBuilder runType(String runType) { this.runType = runType; return this; }
        public AgentRunTraceBuilder status(String status) { this.status = status; return this; }
        public AgentRunTraceBuilder retrievedDocIds(List<String> retrievedDocIds) { this.retrievedDocIds = retrievedDocIds; return this; }
        public AgentRunTraceBuilder toolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; return this; }
        public AgentRunTraceBuilder guardrailResults(Map<String, Object> guardrailResults) { this.guardrailResults = guardrailResults; return this; }
        public AgentRunTraceBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public AgentRunTrace build() {
            AgentRunTrace a = new AgentRunTrace();
            a.runId = runId;
            a.ticketId = ticketId;
            a.runType = runType;
            a.status = status;
            a.retrievedDocIds = retrievedDocIds;
            a.toolCalls = toolCalls;
            a.guardrailResults = guardrailResults;
            a.createdAt = createdAt;
            return a;
        }
    }
}
