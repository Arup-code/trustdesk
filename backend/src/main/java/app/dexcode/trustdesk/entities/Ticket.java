package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.TicketCategory;
import app.dexcode.trustdesk.enums.TicketPriority;
import app.dexcode.trustdesk.enums.TicketSentiment;
import app.dexcode.trustdesk.enums.TicketStatus;
import app.dexcode.trustdesk.persistence.JsonConverters;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Ticket {

    @Id
    @Column(name = "ticket_id")
    @EqualsAndHashCode.Include
    @ToString.Include
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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private TicketStatus status;

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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private TicketCategory category;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private TicketPriority priority;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private TicketSentiment sentiment;

    @Column(name = "should_escalate")
    private Boolean shouldEscalate;

    @Column(name = "reason_summary")
    private String reasonSummary;

    public String getStatus() { return status == null ? null : status.name(); }
    public void setStatus(String status) { this.status = TicketStatus.fromValue(status); }
    public void setStatus(TicketStatus status) { this.status = status; }

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

    public String getCategory() { return category == null ? null : category.name(); }
    public void setCategory(String category) { this.category = TicketCategory.fromValue(category); }
    public void setCategory(TicketCategory category) { this.category = category; }

    public String getPriority() { return priority == null ? null : priority.name(); }
    public void setPriority(String priority) { this.priority = TicketPriority.fromValue(priority); }
    public void setPriority(TicketPriority priority) { this.priority = priority; }

    public String getSentiment() { return sentiment == null ? null : sentiment.name(); }
    public void setSentiment(String sentiment) { this.sentiment = TicketSentiment.fromValue(sentiment); }
    public void setSentiment(TicketSentiment sentiment) { this.sentiment = sentiment; }

    public Boolean getShouldEscalate() { return shouldEscalate; }
    public void setShouldEscalate(Boolean shouldEscalate) { this.shouldEscalate = shouldEscalate; }

    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }

    @Builder
    public Ticket(String ticketId, String customerId, String orderId, String channel, String subject, String body,
                  Instant createdAt, String status, String expectedCategory, String expectedPriority,
                  String expectedSentiment, Boolean expectedEscalation, List<String> expectedActions,
                  String category, String priority, String sentiment, Boolean shouldEscalate,
                  String reasonSummary) {
        this.ticketId = ticketId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.channel = channel;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
        this.status = TicketStatus.fromValue(status);
        this.expectedCategory = expectedCategory;
        this.expectedPriority = expectedPriority;
        this.expectedSentiment = expectedSentiment;
        this.expectedEscalation = expectedEscalation;
        this.expectedActions = expectedActions;
        this.category = TicketCategory.fromValue(category);
        this.priority = TicketPriority.fromValue(priority);
        this.sentiment = TicketSentiment.fromValue(sentiment);
        this.shouldEscalate = shouldEscalate;
        this.reasonSummary = reasonSummary;
    }
}
