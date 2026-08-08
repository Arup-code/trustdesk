package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.DraftReplyStatus;
import app.dexcode.trustdesk.persistence.JsonConverters;
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
@Table(name = "draft_replies")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class DraftReply {

    @Id
    @Column(name = "draft_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String draftId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private DraftReplyStatus status;

    @Lob
    private String body;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> citations;

    @Column(name = "created_at")
    private Instant createdAt;

    public String getStatus() { return status == null ? null : status.name(); }
    public void setStatus(String status) { this.status = DraftReplyStatus.fromValue(status); }
    public void setStatus(DraftReplyStatus status) { this.status = status; }

    @Builder
    public DraftReply(String draftId, String ticketId, String status, String body, List<String> citations, Instant createdAt) {
        this.draftId = draftId;
        this.ticketId = ticketId;
        this.status = DraftReplyStatus.fromValue(status);
        this.body = body;
        this.citations = citations;
        this.createdAt = createdAt;
    }
}
