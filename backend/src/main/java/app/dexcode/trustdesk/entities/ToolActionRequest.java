package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(
    name = "tool_action_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tool_action_idempotency",
        columnNames = {"tool_name", "idempotency_key"})
)
public class ToolActionRequest {

    @Id
    @Column(name = "action_id")
    private String actionId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "tool_name")
    private String toolName;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> payload;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_human_approval")
    private boolean requiresHumanApproval;

    private String status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at")
    private Instant createdAt;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> result;

    public ToolActionRequest() {}

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public void setRequiresHumanApproval(boolean requiresHumanApproval) { this.requiresHumanApproval = requiresHumanApproval; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolActionRequest that = (ToolActionRequest) o;
        return Objects.equals(actionId, that.actionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionId);
    }

    @Override
    public String toString() {
        return "ToolActionRequest{actionId='" + actionId + "'}";
    }

    public static ToolActionRequestBuilder builder() {
        return new ToolActionRequestBuilder();
    }

    public static class ToolActionRequestBuilder {
        private String actionId;
        private String ticketId;
        private String toolName;
        private Map<String, Object> payload;
        private String riskLevel;
        private boolean requiresHumanApproval;
        private String status;
        private String idempotencyKey;
        private Instant createdAt;
        private Map<String, Object> result;

        public ToolActionRequestBuilder actionId(String actionId) { this.actionId = actionId; return this; }
        public ToolActionRequestBuilder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        public ToolActionRequestBuilder toolName(String toolName) { this.toolName = toolName; return this; }
        public ToolActionRequestBuilder payload(Map<String, Object> payload) { this.payload = payload; return this; }
        public ToolActionRequestBuilder riskLevel(String riskLevel) { this.riskLevel = riskLevel; return this; }
        public ToolActionRequestBuilder requiresHumanApproval(boolean requiresHumanApproval) { this.requiresHumanApproval = requiresHumanApproval; return this; }
        public ToolActionRequestBuilder status(String status) { this.status = status; return this; }
        public ToolActionRequestBuilder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public ToolActionRequestBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public ToolActionRequestBuilder result(Map<String, Object> result) { this.result = result; return this; }

        public ToolActionRequest build() {
            ToolActionRequest t = new ToolActionRequest();
            t.actionId = actionId;
            t.ticketId = ticketId;
            t.toolName = toolName;
            t.payload = payload;
            t.riskLevel = riskLevel;
            t.requiresHumanApproval = requiresHumanApproval;
            t.status = status;
            t.idempotencyKey = idempotencyKey;
            t.createdAt = createdAt;
            t.result = result;
            return t;
        }
    }
}
