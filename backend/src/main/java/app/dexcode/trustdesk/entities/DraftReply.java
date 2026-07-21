package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "draft_replies")
public class DraftReply {

    @Id
    @Column(name = "draft_id")
    private String draftId;

    @Column(name = "ticket_id")
    private String ticketId;

    private String status;

    @Lob
    private String body;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> citations;

    @Column(name = "created_at")
    private Instant createdAt;

    public DraftReply() {}

    public String getDraftId() { return draftId; }
    public void setDraftId(String draftId) { this.draftId = draftId; }

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public List<String> getCitations() { return citations; }
    public void setCitations(List<String> citations) { this.citations = citations; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftReply that = (DraftReply) o;
        return Objects.equals(draftId, that.draftId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(draftId);
    }

    @Override
    public String toString() {
        return "DraftReply{draftId='" + draftId + "'}";
    }

    public static DraftReplyBuilder builder() {
        return new DraftReplyBuilder();
    }

    public static class DraftReplyBuilder {
        private String draftId;
        private String ticketId;
        private String status;
        private String body;
        private List<String> citations;
        private Instant createdAt;

        public DraftReplyBuilder draftId(String draftId) { this.draftId = draftId; return this; }
        public DraftReplyBuilder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        public DraftReplyBuilder status(String status) { this.status = status; return this; }
        public DraftReplyBuilder body(String body) { this.body = body; return this; }
        public DraftReplyBuilder citations(List<String> citations) { this.citations = citations; return this; }
        public DraftReplyBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public DraftReply build() {
            DraftReply d = new DraftReply();
            d.draftId = draftId;
            d.ticketId = ticketId;
            d.status = status;
            d.body = body;
            d.citations = citations;
            d.createdAt = createdAt;
            return d;
        }
    }
}
