package app.dexcode.trustdesk.config;

import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final String dataDir;
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public DataSeeder(
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        TicketRepository ticketRepository,
        @Value("${app.seed.data-dir:../data}") String dataDir
    ) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.dataDir = dataDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File customersFile = new File(dataDir, "customers.json");
        if (customerRepository.count() == 0 && customersFile.exists()) {
            for (SeedCustomer c : mapper.readValue(customersFile, SeedCustomer[].class)) {
                customerRepository.save(Customer.builder()
                    .customerId(c.customerId()).name(c.name()).email(c.email())
                    .tier(c.tier()).country(c.country()).createdAt(c.createdAt())
                    .verified(c.verified())
                    .tags(c.tags() == null ? List.of() : c.tags()).build());
            }
        }

        File ordersFile = new File(dataDir, "orders.json");
        if (orderRepository.count() == 0 && ordersFile.exists()) {
            for (SeedOrder o : mapper.readValue(ordersFile, SeedOrder[].class)) {
                orderRepository.save(Order.builder()
                    .orderId(o.orderId()).customerId(o.customerId()).status(o.status())
                    .placedAt(o.placedAt()).deliveredAt(o.deliveredAt())
                    .eligibleReturnUntil(o.eligibleReturnUntil()).total(o.total())
                    .currency(o.currency()).paymentStatus(o.paymentStatus())
                    .trackingNumber(o.trackingNumber())
                    .items(o.items() == null ? List.of() : o.items()).build());
            }
        }

        File ticketsFile = new File(dataDir, "tickets.json");
        if (ticketRepository.count() == 0 && ticketsFile.exists()) {
            for (SeedTicket t : mapper.readValue(ticketsFile, SeedTicket[].class)) {
                ticketRepository.save(Ticket.builder()
                    .ticketId(t.ticketId()).customerId(t.customerId()).orderId(t.orderId())
                    .channel(t.channel()).subject(t.subject()).body(t.body())
                    .createdAt(t.createdAt()).status(t.status())
                    .expectedCategory(t.expectedCategory()).expectedPriority(t.expectedPriority())
                    .expectedSentiment(t.expectedSentiment()).expectedEscalation(t.expectedEscalation())
                    .expectedActions(t.expectedActions() == null ? List.of() : t.expectedActions())
                    .build());
            }
        }
    }

    private record SeedCustomer(
        String customerId, String name, String email, String tier, String country,
        Instant createdAt, boolean verified, List<String> tags) {}

    private record SeedOrder(
        String orderId, String customerId, String status, Instant placedAt,
        Instant deliveredAt, Instant eligibleReturnUntil, BigDecimal total,
        String currency, String paymentStatus, String trackingNumber,
        List<Map<String, Object>> items) {}

    private record SeedTicket(
        String ticketId, String customerId, String orderId, String channel,
        String subject, String body, Instant createdAt, String status,
        String expectedCategory, String expectedPriority, String expectedSentiment,
        Boolean expectedEscalation, List<String> expectedActions) {}
}
