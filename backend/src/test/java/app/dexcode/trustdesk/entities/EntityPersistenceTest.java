package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EntityPersistenceTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;
    @Autowired private EvalRunRepository evalRunRepository;

    @Test
    void savesAndReloadsCustomerWithTags() {
        customerRepository.saveAndFlush(Customer.builder()
            .customerId("cus_1001").name("Asha Rao").email("asha@example.com")
            .tier("gold").country("IN").createdAt(Instant.parse("2025-01-01T00:00:00Z"))
            .verified(true).tags(List.of("vip", "beta")).build());

        Customer reloaded = customerRepository.findById("cus_1001").orElseThrow();
        assertEquals(List.of("vip", "beta"), reloaded.getTags());
    }

    @Test
    void savesAndReloadsOrderWithItems() {
        orderRepository.saveAndFlush(Order.builder()
            .orderId("ord_5001").customerId("cus_1001").status("delivered")
            .placedAt(Instant.parse("2025-01-05T00:00:00Z"))
            .deliveredAt(Instant.parse("2025-01-10T00:00:00Z"))
            .eligibleReturnUntil(Instant.parse("2025-02-10T00:00:00Z"))
            .total(new java.math.BigDecimal("49.99")).currency("INR")
            .paymentStatus("paid").trackingNumber("TRK123")
            .items(List.of(Map.of("sku", "BG-AIRPODS-01", "qty", 1))).build());

        Order reloaded = orderRepository.findById("ord_5001").orElseThrow();
        assertEquals("BG-AIRPODS-01", reloaded.getItems().get(0).get("sku"));
    }

    @Test
    void savesAndReloadsTicketWithExpectedAndRealFields() {
        ticketRepository.saveAndFlush(Ticket.builder()
            .ticketId("tkt_9001").customerId("cus_1001").orderId("ord_5001")
            .channel("email").subject("Damaged earbuds")
            .body("My BlueBuds Air arrived with the left earbud cracked.")
            .createdAt(Instant.parse("2025-02-01T00:00:00Z")).status("open")
            .expectedCategory("refund").expectedPriority("medium")
            .expectedEscalation(false)
            .expectedActions(List.of("create_replacement_order")).build());

        Ticket reloaded = ticketRepository.findById("tkt_9001").orElseThrow();
        assertEquals(List.of("create_replacement_order"), reloaded.getExpectedActions());
        assertNull(reloaded.getCategory());
    }

    @Test
    void enforcesIdempotencyUniqueConstraintOnToolActionRequest() {
        toolActionRequestRepository.saveAndFlush(ToolActionRequest.builder()
            .actionId("act_1").ticketId("tkt_9001").toolName("create_replacement_order")
            .payload(Map.of("order_id", "ord_5001")).riskLevel("medium")
            .requiresHumanApproval(true).status("approval_required")
            .idempotencyKey("tkt_9001-replacement-1").createdAt(Instant.now()).build());

        ToolActionRequest duplicate = ToolActionRequest.builder()
            .actionId("act_2").ticketId("tkt_9001").toolName("create_replacement_order")
            .payload(Map.of("order_id", "ord_5001")).riskLevel("medium")
            .requiresHumanApproval(true).status("approval_required")
            .idempotencyKey("tkt_9001-replacement-1").createdAt(Instant.now()).build();

        assertThrows(DataIntegrityViolationException.class,
            () -> toolActionRequestRepository.saveAndFlush(duplicate));
    }

    @Test
    void savesAndReloadsApprovalTraceAndEvalRun() {
        approvalRepository.saveAndFlush(Approval.builder()
            .approvalId("appr_1").actionId("act_1").reviewerId("manager1")
            .decision("approved").reason("Within return window")
            .createdAt(Instant.now()).build());
        assertTrue(approvalRepository.findById("appr_1").isPresent());

        agentRunTraceRepository.saveAndFlush(AgentRunTrace.builder()
            .runId("run_1").ticketId("tkt_9001").runType("triage").status("completed")
            .retrievedDocIds(List.of("KB-REFUND-001"))
            .toolCalls(List.of(Map.of("tool_name", "create_replacement_order")))
            .guardrailResults(Map.of("flagged", false)).createdAt(Instant.now()).build());
        AgentRunTrace reloadedTrace = agentRunTraceRepository.findById("run_1").orElseThrow();
        assertEquals(List.of("KB-REFUND-001"), reloadedTrace.getRetrievedDocIds());

        evalRunRepository.saveAndFlush(EvalRun.builder()
            .evalRunId("eval_run_1").startedAt(Instant.now()).completedAt(Instant.now())
            .totalCases(8).metrics(Map.of("triage_accuracy", 0.875))
            .caseResults(List.of(Map.of("case_id", "eval_001", "passed", true))).build());
        assertTrue(evalRunRepository.findById("eval_run_1").isPresent());
    }
}
