package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.client.AiServiceClient;
import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.dto.TriageRequest;
import app.dexcode.trustdesk.dto.TriageResponse;
import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final AiServiceClient aiServiceClient;
    private final AgentRunTraceRepository agentRunTraceRepository;

    public TicketService(
        TicketRepository ticketRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        AiServiceClient aiServiceClient,
        AgentRunTraceRepository agentRunTraceRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.aiServiceClient = aiServiceClient;
        this.agentRunTraceRepository = agentRunTraceRepository;
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
}
