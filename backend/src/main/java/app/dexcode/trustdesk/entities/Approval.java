package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.ApprovalDecision;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approvals")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Approval {

    @Id
    @Column(name = "approval_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String approvalId;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "reviewer_id")
    private String reviewerId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private ApprovalDecision decision;
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    public String getDecision() { return decision == null ? null : decision.name(); }
    public void setDecision(String decision) { this.decision = ApprovalDecision.fromValue(decision); }
    public void setDecision(ApprovalDecision decision) { this.decision = decision; }

    @Builder
    public Approval(String approvalId, String actionId, String reviewerId, String decision, String reason, Instant createdAt) {
        this.approvalId = approvalId;
        this.actionId = actionId;
        this.reviewerId = reviewerId;
        this.decision = ApprovalDecision.fromValue(decision);
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
