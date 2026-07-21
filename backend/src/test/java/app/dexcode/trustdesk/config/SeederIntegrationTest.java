package app.dexcode.trustdesk.config;

import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class SeederIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;

    @DynamicPropertySource
    static void seedDir(DynamicPropertyRegistry registry) throws IOException {
        Files.writeString(tempDir.resolve("customers.json"), """
            [{"customer_id":"cus_1001","name":"Asha Rao","email":"asha@example.com",
              "tier":"gold","country":"IN","created_at":"2025-01-01T00:00:00Z",
              "verified":true,"tags":["vip"]}]
            """);
        Files.writeString(tempDir.resolve("orders.json"), """
            [{"order_id":"ord_5001","customer_id":"cus_1001","status":"delivered",
              "placed_at":"2025-01-05T00:00:00Z","delivered_at":"2025-01-10T00:00:00Z",
              "eligible_return_until":"2025-02-10T00:00:00Z","total":49.99,
              "currency":"INR","payment_status":"paid","tracking_number":"TRK123",
              "items":[{"sku":"BG-AIRPODS-01","qty":1}]}]
            """);
        Files.writeString(tempDir.resolve("tickets.json"), """
            [{"ticket_id":"tkt_9001","customer_id":"cus_1001","order_id":"ord_5001",
              "channel":"email","subject":"Damaged earbuds",
              "body":"My BlueBuds Air arrived with the left earbud cracked.",
              "created_at":"2025-02-01T00:00:00Z","status":"open",
              "expected_category":"refund","expected_priority":"medium",
              "expected_sentiment":"frustrated","expected_escalation":false,
              "expected_actions":["create_replacement_order"]}]
            """);
        registry.add("app.seed.data-dir", tempDir::toString);
    }

    @Test
    void seedsCustomersOrdersAndTicketsExactlyOnce() throws Exception {
        DataSeeder dataSeeder = new DataSeeder(customerRepository, orderRepository, ticketRepository, tempDir.toString());
        dataSeeder.run(null);

        assertEquals(1, customerRepository.count());
        assertEquals(1, orderRepository.count());
        assertEquals(1, ticketRepository.count());
    }

    @Test
    void reRunningSeederDoesNotDuplicateRows() throws Exception {
        DataSeeder dataSeeder = new DataSeeder(customerRepository, orderRepository, ticketRepository, tempDir.toString());
        dataSeeder.run(null);
        dataSeeder.run(null);
        assertEquals(1, customerRepository.count());
    }
}
