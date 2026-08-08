package app.dexcode.trustdesk.dto;

import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;

public record TicketDetailResponse(
    String ticketId, String subject, String body, String channel, String status,
    String category, String priority, String sentiment,
    Boolean shouldEscalate, String reasonSummary,
    CustomerSummary customer, OrderSummary order
) {
    public record CustomerSummary(
        String customerId, String name, String email, String tier, String country, boolean verified) {}

    public record OrderSummary(
        String orderId, String status, String trackingNumber, String eligibleReturnUntil) {}

    public static TicketDetailResponse from(Ticket ticket, Customer customer, Order order) {
        CustomerSummary customerSummary = customer == null ? null : new CustomerSummary(
            customer.getCustomerId(), customer.getName(), customer.getEmail(),
            customer.getTier(), customer.getCountry(), customer.isVerified());
        OrderSummary orderSummary = order == null ? null : new OrderSummary(
            order.getOrderId(), order.getStatus(), order.getTrackingNumber(),
            order.getEligibleReturnUntil() == null ? null : order.getEligibleReturnUntil().toString());
        return new TicketDetailResponse(
            ticket.getTicketId(), ticket.getSubject(), ticket.getBody(), ticket.getChannel(),
            ticket.getStatus(), ticket.getCategory(), ticket.getPriority(), ticket.getSentiment(),
            ticket.getShouldEscalate(), ticket.getReasonSummary(), customerSummary, orderSummary);
    }
}
