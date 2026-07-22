package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "order_id")
    private String orderId;

    private String channel;
    private String subject;

    @Lob
    private String body;

    @Column(name = "created_at")
    private Instant createdAt;

    private String status;

    @Column(name = "expected_category")
    private String expectedCategory;

    @Column(name = "expected_priority")
    private String expectedPriority;

    @Column(name = "expected_sentiment")
    private String expectedSentiment;

    @Column(name = "expected_escalation")
    private Boolean expectedEscalation;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(name = "expected_actions", columnDefinition = "TEXT")
    private List<String> expectedActions;

    private String category;
    private String priority;
    private String sentiment;

    @Column(name = "should_escalate")
    private Boolean shouldEscalate;

    @Column(name = "reason_summary")
    private String reasonSummary;

    public Ticket() {}

    // Getters and Setters
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @JsonIgnore
    public String getExpectedCategory() { return expectedCategory; }
    public void setExpectedCategory(String expectedCategory) { this.expectedCategory = expectedCategory; }

    @JsonIgnore
    public String getExpectedPriority() { return expectedPriority; }
    public void setExpectedPriority(String expectedPriority) { this.expectedPriority = expectedPriority; }

    @JsonIgnore
    public String getExpectedSentiment() { return expectedSentiment; }
    public void setExpectedSentiment(String expectedSentiment) { this.expectedSentiment = expectedSentiment; }

    @JsonIgnore
    public Boolean getExpectedEscalation() { return expectedEscalation; }
    public void setExpectedEscalation(Boolean expectedEscalation) { this.expectedEscalation = expectedEscalation; }

    @JsonIgnore
    public List<String> getExpectedActions() { return expectedActions; }
    public void setExpectedActions(List<String> expectedActions) { this.expectedActions = expectedActions; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public Boolean getShouldEscalate() { return shouldEscalate; }
    public void setShouldEscalate(Boolean shouldEscalate) { this.shouldEscalate = shouldEscalate; }

    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Objects.equals(ticketId, ticket.ticketId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticketId);
    }

    @Override
    public String toString() {
        return "Ticket{" + "ticketId='" + ticketId + '\'' + '}';
    }

    public static TicketBuilder builder() {
        return new TicketBuilder();
    }

    public static class TicketBuilder {
        private String ticketId;
        private String customerId;
        private String orderId;
        private String channel;
        private String subject;
        private String body;
        private Instant createdAt;
        private String status;
        private String expectedCategory;
        private String expectedPriority;
        private String expectedSentiment;
        private Boolean expectedEscalation;
        private List<String> expectedActions;
        private String category;
        private String priority;
        private String sentiment;
        private Boolean shouldEscalate;
        private String reasonSummary;

        public TicketBuilder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        public TicketBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public TicketBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public TicketBuilder channel(String channel) { this.channel = channel; return this; }
        public TicketBuilder subject(String subject) { this.subject = subject; return this; }
        public TicketBuilder body(String body) { this.body = body; return this; }
        public TicketBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public TicketBuilder status(String status) { this.status = status; return this; }
        public TicketBuilder expectedCategory(String expectedCategory) { this.expectedCategory = expectedCategory; return this; }
        public TicketBuilder expectedPriority(String expectedPriority) { this.expectedPriority = expectedPriority; return this; }
        public TicketBuilder expectedSentiment(String expectedSentiment) { this.expectedSentiment = expectedSentiment; return this; }
        public TicketBuilder expectedEscalation(Boolean expectedEscalation) { this.expectedEscalation = expectedEscalation; return this; }
        public TicketBuilder expectedActions(List<String> expectedActions) { this.expectedActions = expectedActions; return this; }
        public TicketBuilder category(String category) { this.category = category; return this; }
        public TicketBuilder priority(String priority) { this.priority = priority; return this; }
        public TicketBuilder sentiment(String sentiment) { this.sentiment = sentiment; return this; }
        public TicketBuilder shouldEscalate(Boolean shouldEscalate) { this.shouldEscalate = shouldEscalate; return this; }
        public TicketBuilder reasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; return this; }

        public Ticket build() {
            Ticket t = new Ticket();
            t.ticketId = ticketId;
            t.customerId = customerId;
            t.orderId = orderId;
            t.channel = channel;
            t.subject = subject;
            t.body = body;
            t.createdAt = createdAt;
            t.status = status;
            t.expectedCategory = expectedCategory;
            t.expectedPriority = expectedPriority;
            t.expectedSentiment = expectedSentiment;
            t.expectedEscalation = expectedEscalation;
            t.expectedActions = expectedActions;
            t.category = category;
            t.priority = priority;
            t.sentiment = sentiment;
            t.shouldEscalate = shouldEscalate;
            t.reasonSummary = reasonSummary;
            return t;
        }
    }
}
