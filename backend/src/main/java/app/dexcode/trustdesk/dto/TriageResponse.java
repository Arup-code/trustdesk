package app.dexcode.trustdesk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TriageResponse(
    String category,
    String priority,
    String sentiment,
    @JsonProperty("should_escalate") boolean shouldEscalate,
    @JsonProperty("reason_summary") String reasonSummary,
    @JsonProperty("guardrail_flagged") boolean guardrailFlagged,
    @JsonProperty("guardrail_category") String guardrailCategory
) {}
