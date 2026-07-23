package app.dexcode.trustdesk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record TriageRequest(
    @JsonProperty("ticket_id") String ticketId,
    String subject,
    String body,
    Map<String, Object> customer,
    Map<String, Object> order
) {}
