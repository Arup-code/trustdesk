package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.DraftRequest;
import app.dexcode.trustdesk.dto.DraftResponse;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.DraftReplyRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.data-dir=../data")
class DraftControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DraftReplyRepository draftReplyRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;
    @MockBean private AiServiceClient aiServiceClient;

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

    @Test
    void draftPersistsReplyTraceAndPendingToolAction() throws Exception {
        when(aiServiceClient.draft(any(DraftRequest.class))).thenReturn(new DraftResponse(
            "I'm sorry to hear about the damage. We can offer a replacement. [KB-REFUND-001]",
            List.of("KB-REFUND-001"),
            List.of(new DraftResponse.RecommendedAction(
                "create_replacement_order", true, "Damaged item reported within policy window.")),
            "generated",
            List.of("KB-REFUND-001"),
            false,
            null));

        mockMvc.perform(post("/tickets/tkt_9001/draft-reply")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("generated"))
            .andExpect(jsonPath("$.citations[0]").value("KB-REFUND-001"));

        var drafts = draftReplyRepository.findAll();
        Assertions.assertTrue(drafts.stream().anyMatch(
            d -> "tkt_9001".equals(d.getTicketId()) && "generated".equals(d.getStatus())));

        var traces = agentRunTraceRepository.findAll();
        Assertions.assertTrue(traces.stream().anyMatch(
            t -> "tkt_9001".equals(t.getTicketId()) && "draft_reply".equals(t.getRunType())));

        var pendingActions = toolActionRequestRepository.findAll();
        Assertions.assertTrue(pendingActions.stream().anyMatch(
            a -> "tkt_9001".equals(a.getTicketId())
                && "create_replacement_order".equals(a.getToolName())
                && "approval_required".equals(a.getStatus())));
    }

    @Test
    void draftWithNoRecommendedActionsCreatesNoToolActionRequest() throws Exception {
        when(aiServiceClient.draft(any(DraftRequest.class))).thenReturn(new DraftResponse(
            "I'm unable to confidently answer this request and have escalated it to a human specialist.",
            List.of(),
            List.of(),
            "escalated",
            List.of(),
            false,
            null));

        mockMvc.perform(post("/tickets/tkt_9007/draft-reply")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("escalated"));

        var drafts = draftReplyRepository.findAll();
        Assertions.assertTrue(drafts.stream().anyMatch(
            d -> "tkt_9007".equals(d.getTicketId()) && "escalated".equals(d.getStatus())));

        var pendingActionsForTicket = toolActionRequestRepository.findAll().stream()
            .filter(a -> "tkt_9007".equals(a.getTicketId()))
            .toList();
        Assertions.assertTrue(pendingActionsForTicket.isEmpty());
    }

    @Test
    void draftReturns404ForUnknownTicket() throws Exception {
        mockMvc.perform(post("/tickets/does-not-exist/draft-reply")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void draftRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/tickets/tkt_9001/draft-reply"))
            .andExpect(status().isUnauthorized());
    }
}
