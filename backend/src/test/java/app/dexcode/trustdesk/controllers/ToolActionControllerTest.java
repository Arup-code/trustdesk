package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.data-dir=../data")
class ToolActionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new AuthController.LoginRequest("agent1", "agent123"))))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private void setTicketCategory(String ticketId, String category) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCategory(category);
        ticketRepository.save(ticket);
    }

    @Test
    void requestActionRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/tool-actions")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void requestActionReturns201ForValidRequest() throws Exception {
        setTicketCategory("tkt_9001", "refund");
        String body = objectMapper.writeValueAsString(Map.of(
            "ticket_id", "tkt_9001",
            "tool_name", "create_replacement_order",
            "payload", Map.of(
                "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
                "idempotency_key", "controller-test-happy-path")));

        mockMvc.perform(authed(post("/tool-actions"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("approval_required"))
            .andExpect(jsonPath("$.toolName").value("create_replacement_order"));
    }

    @Test
    void requestActionReturns400ForUnknownTool() throws Exception {
        setTicketCategory("tkt_9001", "refund");
        String body = objectMapper.writeValueAsString(Map.of(
            "ticket_id", "tkt_9001",
            "tool_name", "not_a_real_tool",
            "payload", Map.of("idempotency_key", "controller-test-unknown-tool")));

        mockMvc.perform(authed(post("/tool-actions"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void requestActionReturns403WhenGuardrailFlagged() throws Exception {
        setTicketCategory("tkt_9006", "general");
        agentRunTraceRepository.save(AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9006")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(java.time.Instant.now())
            .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "ticket_id", "tkt_9006",
            "tool_name", "issue_coupon",
            "payload", Map.of(
                "customer_id", "cus_1006", "amount", 500, "reason", "goodwill",
                "idempotency_key", "controller-test-guardrail-denied")));

        mockMvc.perform(authed(post("/tool-actions"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void approveReturns404ForUnknownAction() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "reviewer_id", "manager1", "decision", "approved", "reason", "ok"));

        mockMvc.perform(authed(post("/tool-actions/does-not-exist/approve"))
                .contentType("application/json")
                .content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void executeReturns404ForUnknownAction() throws Exception {
        mockMvc.perform(authed(post("/tool-actions/does-not-exist/execute")))
            .andExpect(status().isNotFound());
    }

    @Test
    void fullLifecycleThroughHttpReturnsExpectedStatusesAtEachStep() throws Exception {
        setTicketCategory("tkt_9001", "refund");
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "ticket_id", "tkt_9001",
            "tool_name", "create_replacement_order",
            "payload", Map.of(
                "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
                "idempotency_key", "controller-test-full-lifecycle")));

        String createdBody = mockMvc.perform(authed(post("/tool-actions"))
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String actionId = objectMapper.readTree(createdBody).get("actionId").asText();

        // execute before approve -> 409
        mockMvc.perform(authed(post("/tool-actions/" + actionId + "/execute")))
            .andExpect(status().isConflict());

        String approveBody = objectMapper.writeValueAsString(Map.of(
            "reviewer_id", "manager1", "decision", "approved", "reason", "within policy"));
        mockMvc.perform(authed(post("/tool-actions/" + actionId + "/approve"))
                .contentType("application/json")
                .content(approveBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("approved"));

        mockMvc.perform(authed(post("/tool-actions/" + actionId + "/execute")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("executed"))
            .andExpect(jsonPath("$.result.replacement_order_id").exists());

        mockMvc.perform(authed(get("/tool-actions").param("ticket_id", "tkt_9001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.actionId == '" + actionId + "')]").exists());
    }
}
