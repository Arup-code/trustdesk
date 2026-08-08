package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.DraftRequest;
import app.dexcode.trustdesk.dto.DraftResponse;
import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.DraftReplyRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
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
    @Autowired private TicketRepository ticketRepository;
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

    private void setTicketCategory(String ticketId, String category) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCategory(category);
        ticketRepository.save(ticket);
    }

    @Test
    void draftPersistsReplyTraceAndPendingToolAction() throws Exception {
        // The auto-created ToolActionRequest now goes through ToolActionService.requestAction(),
        // which validates the ticket's category against the tool catalog (matching the real demo
        // flow where /tickets/{id}/triage always runs before /draft-reply) -- without this, the
        // action would fail the category check and be silently skipped rather than persisted.
        setTicketCategory("tkt_9001", "refund");
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
    void draftBeforeTriageStillCreatesPendingToolAction() throws Exception {
        // Regression test for a whole-branch-review finding: routing the auto-created
        // ToolActionRequest through ToolActionService.requestAction() (which requires a real,
        // persisted ticket category) would otherwise silently re-drop the Must-Have
        // create_replacement_order recommendation whenever draft-reply is called before triage --
        // exactly the ordering-independence Phase 3's draft graph itself was fixed to guarantee.
        // tkt_9008 is untriaged at the start of this test (category is null; DataSeeder never
        // sets it, only runTriage() does).
        when(aiServiceClient.triage(any(TriageRequest.class))).thenReturn(new TriageResponse(
            "refund", "medium", "frustrated", false,
            "Damaged item reported within return window.", false, null));
        when(aiServiceClient.draft(any(DraftRequest.class))).thenReturn(new DraftResponse(
            "I'm sorry to hear about the damage. We can offer a replacement. [KB-REFUND-001]",
            List.of("KB-REFUND-001"),
            List.of(new DraftResponse.RecommendedAction(
                "create_replacement_order", true, "Damaged item reported within policy window.")),
            "generated",
            List.of("KB-REFUND-001"),
            false,
            null));

        mockMvc.perform(post("/tickets/tkt_9008/draft-reply")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("generated"));

        Ticket ticket = ticketRepository.findById("tkt_9008").orElseThrow();
        Assertions.assertEquals("refund", ticket.getCategory());

        var pendingActions = toolActionRequestRepository.findAll();
        Assertions.assertTrue(pendingActions.stream().anyMatch(
            a -> "tkt_9008".equals(a.getTicketId())
                && "create_replacement_order".equals(a.getToolName())
                && "approval_required".equals(a.getStatus())));
    }

    @Test
    void draftWithNoRecommendedActionsCreatesNoToolActionRequest() throws Exception {
        // Pre-set the category so this test exercises only the "no recommended actions" behavior
        // it's named for, not the separate implicit-triage path covered by
        // draftBeforeTriageStillCreatesPendingToolAction above.
        setTicketCategory("tkt_9007", "account_security");
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
