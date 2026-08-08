package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.exception.InvalidToolActionStateException;
import app.dexcode.trustdesk.exception.ToolActionDeniedException;
import app.dexcode.trustdesk.exception.ToolActionValidationException;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "app.seed.data-dir=../data")
class ToolActionServiceTest {

    @Autowired private ToolActionService toolActionService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;
    @Autowired private app.dexcode.trustdesk.repositories.AgentRunTraceRepository agentRunTraceRepository;

    private void setTicketCategory(String ticketId, String category) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCategory(category);
        ticketRepository.save(ticket);
    }

    @Test
    void requestActionRejectsUnknownTool() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "not_a_real_tool", Map.of("idempotency_key", "k1")));
        assertTrue(ex.getMessage().contains("not_a_real_tool"));
    }

    @Test
    void requestActionRejectsMissingRequiredField() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
                "order_id", "ord_5001", "idempotency_key", "k2")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void requestActionRejectsDisallowedCategory() {
        setTicketCategory("tkt_9002", "shipping");
        var ex = assertThrows(ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9002", "create_replacement_order", Map.of(
                "order_id", "ord_5002", "sku", "BG-CASE-14", "reason", "damaged",
                "idempotency_key", "k3")));
        assertTrue(ex.getMessage().contains("shipping"));
    }

    @Test
    void requestActionIsIdempotentOnRetry() {
        setTicketCategory("tkt_9001", "refund");
        Map<String, Object> payload = Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-retry-test");

        ToolActionRequest first = toolActionService.requestAction("tkt_9001", "create_replacement_order", payload);
        ToolActionRequest second = toolActionService.requestAction("tkt_9001", "create_replacement_order", payload);

        assertEquals(first.getActionId(), second.getActionId());
        long count = toolActionRequestRepository.findAll().stream()
            .filter(a -> "tkt_9001-replacement-retry-test".equals(a.getIdempotencyKey()))
            .count();
        assertEquals(1, count);
    }

    @Test
    void executeBeforeApproveIsRejected() {
        setTicketCategory("tkt_9001", "refund");
        ToolActionRequest action = toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-execute-before-approve"));

        assertThrows(InvalidToolActionStateException.class, () ->
            toolActionService.execute(action.getActionId()));
    }

    @Test
    void fullHappyPathRequestApproveExecute() {
        setTicketCategory("tkt_9001", "refund");
        ToolActionRequest requested = toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-happy-path"));
        assertEquals("approval_required", requested.getStatus());

        Approval approval = toolActionService.approve(
            requested.getActionId(), "manager1", "approved", "Within policy");
        assertEquals("approved", approval.getDecision());

        ToolActionRequest executed = toolActionService.execute(requested.getActionId());
        assertEquals("executed", executed.getStatus());
        assertNotNull(executed.getResult());
        assertTrue(executed.getResult().containsKey("replacement_order_id"));

        ToolActionRequest reExecuted = toolActionService.execute(requested.getActionId());
        assertEquals(
            executed.getResult().get("replacement_order_id"),
            reExecuted.getResult().get("replacement_order_id"));
    }

    @Test
    void requestActionDeniedWhenTicketHasFlaggedGuardrailTraceAndToolIsIssueCoupon() {
        setTicketCategory("tkt_9006", "general");
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9006")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(java.time.Instant.now())
            .build());

        var ex = assertThrows(ToolActionDeniedException.class, () ->
            toolActionService.requestAction("tkt_9006", "issue_coupon", Map.of(
                "customer_id", "cus_1006", "amount", 500, "reason", "goodwill",
                "idempotency_key", "tkt_9006-coupon-denied")));
        assertTrue(ex.getMessage().contains("issue_coupon"));
    }

    @Test
    void guardrailDenialTakesPrecedenceOverCategoryRejectionForTheSameRequest() {
        // Regression test for a whole-branch-review finding: a coupon-injection-flagged ticket's
        // real post-triage category is "account_security" (triage_graph.py's flagged-path
        // default), which isn't in issue_coupon's allowed_categories either -- so if the category
        // check ran first, the request would still be rejected, but with the wrong exception type
        // and a misleading message, leaving the guardrail-specific denial effectively unreachable
        // in the one flow it exists to protect. Uses "account_security" deliberately (not
        // "general", which the other denial test uses) to prove the guardrail check, not the
        // category check, is what fires.
        setTicketCategory("tkt_9008", "account_security");
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9008")
            .runType("triage")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(java.time.Instant.now())
            .build());

        var ex = assertThrows(ToolActionDeniedException.class, () ->
            toolActionService.requestAction("tkt_9008", "issue_coupon", Map.of(
                "customer_id", "cus_1003", "amount", 500, "reason", "goodwill",
                "idempotency_key", "tkt_9008-coupon-precedence-check")));
        assertTrue(ex.getMessage().contains("issue_coupon"));
    }

    @Test
    void requestActionAllowsOtherToolsEvenWhenTicketHasFlaggedTrace() {
        // The guardrail denial must be scoped to issue_coupon only -- a flagged trace must not
        // block an unrelated, legitimate tool like create_replacement_order.
        setTicketCategory("tkt_9004", "warranty");
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9004")
            .runType("triage")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(java.time.Instant.now())
            .build());

        ToolActionRequest action = toolActionService.requestAction("tkt_9004", "create_replacement_order", Map.of(
            "order_id", "ord_5004", "sku", "BG-TAB-10", "reason", "battery swelling",
            "idempotency_key", "tkt_9004-replacement-despite-flag"));

        assertEquals("approval_required", action.getStatus());
    }

    @Test
    void requestActionNotDeniedWhenTraceIsNotFlagged() {
        setTicketCategory("tkt_9002", "general");
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9002")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", false, "category", ""))
            .createdAt(java.time.Instant.now())
            .build());

        ToolActionRequest action = toolActionService.requestAction("tkt_9002", "issue_coupon", Map.of(
            "customer_id", "cus_1002", "amount", 300, "reason", "shipping delay goodwill",
            "idempotency_key", "tkt_9002-coupon-unflagged"));

        assertEquals("approval_required", action.getStatus());
    }

    @Test
    void requestActionNotDeniedWhenTicketHasNoTrace() {
        setTicketCategory("tkt_9003", "general");

        ToolActionRequest action = toolActionService.requestAction("tkt_9003", "issue_coupon", Map.of(
            "customer_id", "cus_1004", "amount", 200, "reason", "goodwill",
            "idempotency_key", "tkt_9003-coupon-no-trace"));

        assertEquals("approval_required", action.getStatus());
    }

    @Test
    void requestActionUsesMostRecentTraceNotOldest() {
        // Older trace flagged, newest trace clean -- must NOT deny.
        setTicketCategory("tkt_9005", "general");
        java.time.Instant now = java.time.Instant.now();

        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9005")
            .runType("triage")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(now.minusSeconds(60))
            .build());
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9005")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", false, "category", ""))
            .createdAt(now)
            .build());

        ToolActionRequest action = toolActionService.requestAction("tkt_9005", "issue_coupon", Map.of(
            "customer_id", "cus_1003", "amount", 400, "reason", "goodwill",
            "idempotency_key", "tkt_9005-coupon-recent-clears-old-flag"));
        assertEquals("approval_required", action.getStatus());
    }

    @Test
    void requestActionDeniedWhenMostRecentTraceIsFlaggedEvenIfOlderTraceWasNot() {
        // Older trace clean, newest trace flagged -- must deny. Uses a ticket not touched by any
        // other test in this class so its trace history is deterministic regardless of test order.
        setTicketCategory("tkt_9007", "general");
        java.time.Instant now = java.time.Instant.now();

        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9007")
            .runType("triage")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", false, "category", ""))
            .createdAt(now.minusSeconds(60))
            .build());
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9007")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(now)
            .build());

        assertThrows(ToolActionDeniedException.class, () ->
            toolActionService.requestAction("tkt_9007", "issue_coupon", Map.of(
                "customer_id", "cus_1006", "amount", 500, "reason", "goodwill",
                "idempotency_key", "tkt_9007-coupon-newest-flag-wins")));
    }
}
