package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.ToolActionRiskLevel;
import app.dexcode.trustdesk.enums.ToolActionStatus;
import app.dexcode.trustdesk.persistence.JsonConverters;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(
    name = "tool_action_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tool_action_idempotency",
        columnNames = {"tool_name", "idempotency_key"})
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ToolActionRequest {

    @Id
    @Column(name = "action_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String actionId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "tool_name")
    private String toolName;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> payload;

    @Column(name = "risk_level")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private ToolActionRiskLevel riskLevel;

    @Column(name = "requires_human_approval")
    private boolean requiresHumanApproval;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private ToolActionStatus status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at")
    private Instant createdAt;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> result;

    public String getRiskLevel() { return riskLevel == null ? null : riskLevel.name(); }
    public void setRiskLevel(String riskLevel) { this.riskLevel = ToolActionRiskLevel.fromValue(riskLevel); }
    public void setRiskLevel(ToolActionRiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getStatus() { return status == null ? null : status.name(); }
    public void setStatus(String status) { this.status = ToolActionStatus.fromValue(status); }
    public void setStatus(ToolActionStatus status) { this.status = status; }

    @Builder
    public ToolActionRequest(String actionId, String ticketId, String toolName, Map<String, Object> payload,
                             String riskLevel, boolean requiresHumanApproval, String status,
                             String idempotencyKey, Instant createdAt, Map<String, Object> result) {
        this.actionId = actionId;
        this.ticketId = ticketId;
        this.toolName = toolName;
        this.payload = payload;
        this.riskLevel = ToolActionRiskLevel.fromValue(riskLevel);
        this.requiresHumanApproval = requiresHumanApproval;
        this.status = ToolActionStatus.fromValue(status);
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.result = result;
    }
}
