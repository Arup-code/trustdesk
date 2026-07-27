package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
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

    private void setTicketCategory(String ticketId, String category) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCategory(category);
        ticketRepository.save(ticket);
    }

    @Test
    void requestActionRejectsUnknownTool() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "not_a_real_tool", Map.of("idempotency_key", "k1")));
        assertTrue(ex.getMessage().contains("not_a_real_tool"));
    }

    @Test
    void requestActionRejectsMissingRequiredField() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
                "order_id", "ord_5001", "idempotency_key", "k2")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void requestActionRejectsDisallowedCategory() {
        setTicketCategory("tkt_9002", "shipping");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
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

        assertThrows(ToolActionService.InvalidToolActionStateException.class, () ->
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
}
