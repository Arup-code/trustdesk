package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.DraftRequest;
import app.dexcode.trustdesk.dto.DraftResponse;
import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.DraftReply;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.exception.ToolActionDeniedException;
import app.dexcode.trustdesk.exception.ToolActionValidationException;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.DraftReplyRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final AiServiceClient aiServiceClient;
    private final AgentRunTraceRepository agentRunTraceRepository;
    private final DraftReplyRepository draftReplyRepository;
    private final ToolActionService toolActionService;

    public TicketService(
        TicketRepository ticketRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        AiServiceClient aiServiceClient,
        AgentRunTraceRepository agentRunTraceRepository,
        DraftReplyRepository draftReplyRepository,
        ToolActionService toolActionService
    ) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.aiServiceClient = aiServiceClient;
        this.agentRunTraceRepository = agentRunTraceRepository;
        this.draftReplyRepository = draftReplyRepository;
        this.toolActionService = toolActionService;
    }

    public List<Ticket> listTickets() {
        return ticketRepository.findAll();
    }

    public TicketDetailResponse getTicketDetail(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
        Customer customer = ticket.getCustomerId() == null ? null :
            customerRepository.findById(ticket.getCustomerId()).orElse(null);
        Order order = ticket.getOrderId() == null ? null :
            orderRepository.findById(ticket.getOrderId()).orElse(null);
        return TicketDetailResponse.from(ticket, customer, order);
    }

    public TriageResponse runTriage(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
        Customer customer = ticket.getCustomerId() == null ? null :
            customerRepository.findById(ticket.getCustomerId()).orElse(null);
        Order order = ticket.getOrderId() == null ? null :
            orderRepository.findById(ticket.getOrderId()).orElse(null);

        TriageRequest request = new TriageRequest(
            ticket.getTicketId(),
            ticket.getSubject(),
            ticket.getBody(),
            customer == null ? Map.of() : Map.of("tier", customer.getTier(), "verified", customer.isVerified()),
            order == null ? Map.of() : Map.of("status", order.getStatus())
        );
        TriageResponse response = aiServiceClient.triage(request);

        ticket.setCategory(response.category());
        ticket.setPriority(response.priority());
        ticket.setSentiment(response.sentiment());
        ticket.setShouldEscalate(response.shouldEscalate());
        ticket.setReasonSummary(response.reasonSummary());
        ticketRepository.save(ticket);

        AgentRunTrace trace = AgentRunTrace.builder()
            .runId(UUID.randomUUID().toString())
            .ticketId(ticketId)
            .runType("triage")
            .status("completed")
            .retrievedDocIds(List.of())
            .toolCalls(List.of())
            .guardrailResults(Map.of(
                "flagged", response.guardrailFlagged(),
                "category", response.guardrailCategory() == null ? "" : response.guardrailCategory()))
            .createdAt(Instant.now())
            .build();
        agentRunTraceRepository.save(trace);

        return response;
    }

    public DraftResponse generateDraft(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));

        if (ticket.getCategory() == null) {
            // Drafting can legitimately be requested before triage has run (DataSeeder never
            // populates category, only runTriage() does). The draft graph's own recommend_tool
            // node already handles this by self-classifying via a fallback model call when no
            // category is supplied -- but that classification never used to reach the ticket
            // row, so requestAction()'s category-allowlist check below would reject the
            // recommendation regardless, silently dropping the Must-Have create_replacement_order
            // action. Running triage implicitly here means the ticket always has a real,
            // persisted category by the time that check runs, matching what the demo flow
            // (triage, then draft) already produces.
            runTriage(ticketId);
            ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
        }

        Customer customer = ticket.getCustomerId() == null ? null :
            customerRepository.findById(ticket.getCustomerId()).orElse(null);
        Order order = ticket.getOrderId() == null ? null :
            orderRepository.findById(ticket.getOrderId()).orElse(null);

        DraftRequest request = new DraftRequest(
            ticket.getTicketId(),
            ticket.getSubject(),
            ticket.getBody(),
            ticket.getCategory(),
            customer == null ? Map.of() : Map.of("tier", customer.getTier(), "verified", customer.isVerified()),
            order == null ? Map.of() : Map.of("status", order.getStatus())
        );
        DraftResponse response = aiServiceClient.draft(request);

        String draftId = UUID.randomUUID().toString();
        DraftReply draftReply = DraftReply.builder()
            .draftId(draftId)
            .ticketId(ticketId)
            .status(response.status())
            .body(response.body())
            .citations(response.citations() == null ? List.of() : response.citations())
            .createdAt(Instant.now())
            .build();
        draftReplyRepository.save(draftReply);

        AgentRunTrace trace = AgentRunTrace.builder()
            .runId(UUID.randomUUID().toString())
            .ticketId(ticketId)
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(response.retrievedDocIds() == null ? List.of() : response.retrievedDocIds())
            .toolCalls(List.of())
            .guardrailResults(Map.of(
                "flagged", response.guardrailFlagged(),
                "category", response.guardrailCategory() == null ? "" : response.guardrailCategory()))
            .createdAt(Instant.now())
            .build();
        agentRunTraceRepository.save(trace);

        if (response.recommendedActions() != null) {
            List<DraftResponse.RecommendedAction> recommendedActions = response.recommendedActions();
            for (int i = 0; i < recommendedActions.size(); i++) {
                DraftResponse.RecommendedAction action = recommendedActions.get(i);
                // Includes the loop index so two recommendations for the same tool in one
                // draft response (not possible with the current Python draft graph, which only
                // ever recommends 0 or 1 actions, but not guaranteed by this Java code alone)
                // never collide on the (tool_name, idempotency_key) unique constraint.
                String idempotencyKey = draftId + "-" + i + "-" + action.toolName();
                Map<String, Object> payload = buildToolActionPayload(action, order, idempotencyKey);
                try {
                    // Routes through the same catalog/category/guardrail validation gate that
                    // POST /tool-actions enforces, rather than writing the row directly -- an
                    // AI recommendation is exactly the "upstream" the guardrail-denial check
                    // (ToolActionService) exists to not have to trust blindly.
                    toolActionService.requestAction(ticketId, action.toolName(), payload);
                } catch (ToolActionValidationException | ToolActionDeniedException e) {
                    // The draft itself is still valid even if the AI's recommended action doesn't
                    // validate against the real catalog/guardrails (e.g. missing order/item data,
                    // or a flagged ticket) -- log and skip creating a pending action for it rather
                    // than failing the whole draft-reply request.
                    log.warn("Skipping recommended tool action {} for ticket {}: {}",
                        action.toolName(), ticketId, e.getMessage());
                }
            }
        }

        return response;
    }

    private Map<String, Object> buildToolActionPayload(
        DraftResponse.RecommendedAction action, Order order, String idempotencyKey
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", action.reason());
        payload.put("idempotency_key", idempotencyKey);
        if (order != null) {
            payload.put("order_id", order.getOrderId());
            payload.put("amount", order.getTotal());
            if (order.getItems() != null && !order.getItems().isEmpty()) {
                Object sku = order.getItems().get(0).get("sku");
                if (sku != null) {
                    payload.put("sku", sku);
                }
            }
        }
        return payload;
    }
}
