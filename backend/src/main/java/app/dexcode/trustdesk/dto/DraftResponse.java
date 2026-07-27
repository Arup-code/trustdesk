package app.dexcode.trustdesk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DraftResponse(
    String body,
    List<String> citations,
    @JsonProperty("recommended_actions") List<RecommendedAction> recommendedActions,
    String status,
    @JsonProperty("retrieved_doc_ids") List<String> retrievedDocIds,
    @JsonProperty("guardrail_flagged") boolean guardrailFlagged,
    @JsonProperty("guardrail_category") String guardrailCategory
) {
    public record RecommendedAction(
        @JsonProperty("tool_name") String toolName,
        @JsonProperty("requires_human_approval") boolean requiresHumanApproval,
        String reason
    ) {}
}
