package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public TicketService(
        TicketRepository ticketRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
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
}
