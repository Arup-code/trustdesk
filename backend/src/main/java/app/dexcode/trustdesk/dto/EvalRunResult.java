package app.dexcode.trustdesk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record EvalRunResult(
    @JsonProperty("total_cases") int totalCases,
    Map<String, Object> metrics,
    @JsonProperty("case_results") List<Map<String, Object>> caseResults
) {}
