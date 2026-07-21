package app.dexcode.trustdesk.entities;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @Column(name = "approval_id")
    private String approvalId;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "reviewer_id")
    private String reviewerId;

    private String decision;
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    public Approval() {}

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Approval approval = (Approval) o;
        return Objects.equals(approvalId, approval.approvalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(approvalId);
    }

    @Override
    public String toString() {
        return "Approval{approvalId='" + approvalId + "'}";
    }

    public static ApprovalBuilder builder() {
        return new ApprovalBuilder();
    }

    public static class ApprovalBuilder {
        private String approvalId;
        private String actionId;
        private String reviewerId;
        private String decision;
        private String reason;
        private Instant createdAt;

        public ApprovalBuilder approvalId(String approvalId) { this.approvalId = approvalId; return this; }
        public ApprovalBuilder actionId(String actionId) { this.actionId = actionId; return this; }
        public ApprovalBuilder reviewerId(String reviewerId) { this.reviewerId = reviewerId; return this; }
        public ApprovalBuilder decision(String decision) { this.decision = decision; return this; }
        public ApprovalBuilder reason(String reason) { this.reason = reason; return this; }
        public ApprovalBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public Approval build() {
            Approval a = new Approval();
            a.approvalId = approvalId;
            a.actionId = actionId;
            a.reviewerId = reviewerId;
            a.decision = decision;
            a.reason = reason;
            a.createdAt = createdAt;
            return a;
        }
    }
}
