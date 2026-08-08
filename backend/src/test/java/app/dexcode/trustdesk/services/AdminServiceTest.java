package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.dto.AdminResetSummary;
import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.DraftReply;
import app.dexcode.trustdesk.entities.EvalRun;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.ApprovalRepository;
import app.dexcode.trustdesk.repositories.DraftReplyRepository;
import app.dexcode.trustdesk.repositories.EvalRunRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "app.seed.data-dir=../data")
class AdminServiceTest {

    @Autowired private AdminService adminService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private DraftReplyRepository draftReplyRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;
    @Autowired private EvalRunRepository evalRunRepository;

    @Test
    void resetDemoDataClearsGeneratedRowsAndTicketTriageFieldsButKeepsSeedTickets() {
        Ticket ticket = ticketRepository.findById("tkt_9001").orElseThrow();
        ticket.setCategory("refund");
        ticket.setPriority("medium");
        ticket.setSentiment("frustrated");
        ticket.setShouldEscalate(false);
        ticket.setReasonSummary("Damaged item, within return window.");
        ticketRepository.save(ticket);

        ToolActionRequest action = toolActionRequestRepository.save(ToolActionRequest.builder()
            .actionId(UUID.randomUUID().toString())
            .ticketId("tkt_9001")
            .toolName("create_replacement_order")
            .payload(Map.of())
            .riskLevel("low")
            .requiresHumanApproval(true)
            .status("approval_required")
            .idempotencyKey("reset-test-key")
            .createdAt(Instant.now())
            .build());

        approvalRepository.save(Approval.builder()
            .approvalId(UUID.randomUUID().toString())
            .actionId(action.getActionId())
            .reviewerId("manager1")
            .decision("approved")
            .reason("Within policy")
            .createdAt(Instant.now())
            .build());

        draftReplyRepository.save(DraftReply.builder()
            .draftId(UUID.randomUUID().toString())
            .ticketId("tkt_9001")
            .status("generated")
            .body("We're sending a replacement.")
            .citations(List.of("KB-RETURNS-001"))
            .createdAt(Instant.now())
            .build());

        agentRunTraceRepository.save(AgentRunTrace.builder()
            .runId(UUID.randomUUID().toString())
            .ticketId("tkt_9001")
            .runType("triage")
            .status("completed")
            .retrievedDocIds(List.of())
            .toolCalls(List.of())
            .guardrailResults(Map.of("flagged", false, "category", ""))
            .createdAt(Instant.now())
            .build());

        evalRunRepository.save(EvalRun.builder()
            .evalRunId(UUID.randomUUID().toString())
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .totalCases(8)
            .metrics(Map.of("triage_accuracy", 1.0))
            .caseResults(List.of())
            .build());

        AdminResetSummary summary = adminService.resetDemoData();

        assertEquals(0, toolActionRequestRepository.count());
        assertEquals(0, approvalRepository.count());
        assertEquals(0, draftReplyRepository.count());
        assertEquals(0, agentRunTraceRepository.count());
        assertEquals(0, evalRunRepository.count());
        assertEquals(1, summary.toolActionsDeleted());
        assertEquals(1, summary.approvalsDeleted());
        assertEquals(1, summary.draftRepliesDeleted());
        assertEquals(1, summary.agentRunTracesDeleted());
        assertEquals(1, summary.evalRunsDeleted());

        Ticket resetTicket = ticketRepository.findById("tkt_9001").orElseThrow();
        assertNull(resetTicket.getCategory());
        assertNull(resetTicket.getPriority());
        assertNull(resetTicket.getSentiment());
        assertNull(resetTicket.getShouldEscalate());
        assertNull(resetTicket.getReasonSummary());
        assertEquals("open", resetTicket.getStatus());
        assertNotNull(resetTicket.getSubject());

        assertTrue(summary.ticketsReset() >= 1);
        assertTrue(ticketRepository.count() > 0);
    }
}
