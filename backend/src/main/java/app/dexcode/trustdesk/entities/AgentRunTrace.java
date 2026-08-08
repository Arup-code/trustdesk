package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.AgentRunTraceRunType;
import app.dexcode.trustdesk.enums.AgentRunTraceStatus;
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
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "agent_run_traces")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class AgentRunTrace {

    @Id
    @Column(name = "run_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String runId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "run_type")
    @Enumerated(EnumType.STRING)
    private AgentRunTraceRunType runType;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private AgentRunTraceStatus status;

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

    public String getRunType() { return runType == null ? null : runType.name(); }
    public void setRunType(String runType) { this.runType = AgentRunTraceRunType.fromValue(runType); }
    public void setRunType(AgentRunTraceRunType runType) { this.runType = runType; }

    public String getStatus() { return status == null ? null : status.name(); }
    public void setStatus(String status) { this.status = AgentRunTraceStatus.fromValue(status); }
    public void setStatus(AgentRunTraceStatus status) { this.status = status; }

    @Builder
    public AgentRunTrace(String runId, String ticketId, String runType, String status, List<String> retrievedDocIds,
                         List<Map<String, Object>> toolCalls, Map<String, Object> guardrailResults, Instant createdAt) {
        this.runId = runId;
        this.ticketId = ticketId;
        this.runType = AgentRunTraceRunType.fromValue(runType);
        this.status = AgentRunTraceStatus.fromValue(status);
        this.retrievedDocIds = retrievedDocIds;
        this.toolCalls = toolCalls;
        this.guardrailResults = guardrailResults;
        this.createdAt = createdAt;
    }
}
