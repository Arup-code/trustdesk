package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.config.ToolCatalog;
import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.exception.InvalidToolActionStateException;
import app.dexcode.trustdesk.exception.ToolActionDeniedException;
import app.dexcode.trustdesk.exception.ToolActionValidationException;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import app.dexcode.trustdesk.repositories.ApprovalRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ToolActionService {

    private final ToolCatalog toolCatalog;
    private final ToolActionRequestRepository toolActionRequestRepository;
    private final TicketRepository ticketRepository;
    private final ApprovalRepository approvalRepository;
    private final AgentRunTraceRepository agentRunTraceRepository;

    public ToolActionService(
        ToolCatalog toolCatalog,
        ToolActionRequestRepository toolActionRequestRepository,
        TicketRepository ticketRepository,
        ApprovalRepository approvalRepository,
        AgentRunTraceRepository agentRunTraceRepository
    ) {
        this.toolCatalog = toolCatalog;
        this.toolActionRequestRepository = toolActionRequestRepository;
        this.ticketRepository = ticketRepository;
        this.approvalRepository = approvalRepository;
        this.agentRunTraceRepository = agentRunTraceRepository;
    }

    public ToolActionRequest requestAction(String ticketId, String toolName, Map<String, Object> payload) {
        ToolCatalog.ToolDefinition definition = toolCatalog.find(toolName)
            .orElseThrow(() -> new ToolActionValidationException("Unknown tool: " + toolName));

        for (String requiredField : definition.requiredFields()) {
            if (!payload.containsKey(requiredField) || payload.get(requiredField) == null) {
                throw new ToolActionValidationException(
                    "Missing required field for " + toolName + ": " + requiredField);
            }
        }

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));

        // Checked before the category allowlist so a guardrail-flagged ticket is denied for the
        // real reason, not shadowed by a category rejection. This matters concretely: a
        // guardrail-flagged ticket's real triage category often lands on "account_security" (the
        // triage graph's flagged-path default), which isn't in issue_coupon's allowed_categories
        // either -- so ordering this second would still block the request, but for the wrong,
        // misleading reason, and would leave the security-specific denial path effectively
        // unreachable in the one flow it exists to protect.
        if (isGuardrailFlaggedForDisallowedTool(ticketId, toolName)) {
            throw new ToolActionDeniedException(
                "Tool " + toolName + " denied: ticket has a flagged guardrail trace");
        }

        if (ticket.getCategory() == null || !definition.allowedCategories().contains(ticket.getCategory())) {
            throw new ToolActionValidationException(
                "Tool " + toolName + " is not allowed for category " + ticket.getCategory());
        }

        String idempotencyKey = String.valueOf(payload.get("idempotency_key"));

        ToolActionRequest request = ToolActionRequest.builder()
            .actionId(UUID.randomUUID().toString())
            .ticketId(ticketId)
            .toolName(toolName)
            .payload(payload)
            .riskLevel(definition.riskLevel())
            .requiresHumanApproval(definition.requiresHumanApproval())
            .status(definition.requiresHumanApproval() ? "approval_required" : "requested")
            .idempotencyKey(idempotencyKey)
            .createdAt(Instant.now())
            .build();

        try {
            return toolActionRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            return toolActionRequestRepository.findByToolNameAndIdempotencyKey(toolName, idempotencyKey)
                .orElseThrow(() -> e);
        }
    }

    public Approval approve(String actionId, String reviewerId, String decision, String reason) {
        ToolActionRequest action = toolActionRequestRepository.findById(actionId)
            .orElseThrow(() -> new NoSuchElementException("Tool action not found: " + actionId));
        if (!"approval_required".equals(action.getStatus())) {
            throw new InvalidToolActionStateException(
                "Tool action " + actionId + " is not awaiting approval (status=" + action.getStatus() + ")");
        }

        Approval approval = Approval.builder()
            .approvalId(UUID.randomUUID().toString())
            .actionId(actionId)
            .reviewerId(reviewerId)
            .decision(decision)
            .reason(reason)
            .createdAt(Instant.now())
            .build();
        approvalRepository.save(approval);

        action.setStatus("approved".equals(decision) ? "approved" : "rejected");
        toolActionRequestRepository.save(action);
        return approval;
    }

    public List<ToolActionRequest> listForTicket(String ticketId) {
        return toolActionRequestRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    public ToolActionRequest execute(String actionId) {
        ToolActionRequest action = toolActionRequestRepository.findById(actionId)
            .orElseThrow(() -> new NoSuchElementException("Tool action not found: " + actionId));

        if ("executed".equals(action.getStatus())) {
            return action;
        }
        if (!"approved".equals(action.getStatus())) {
            throw new InvalidToolActionStateException(
                "Tool action " + actionId + " is not approved (status=" + action.getStatus() + ")");
        }

        Map<String, Object> result = switch (action.getToolName()) {
            case "create_replacement_order" -> Map.of("replacement_order_id", "ro_" + UUID.randomUUID());
            case "start_refund_review" -> Map.of("refund_review_id", "rr_" + UUID.randomUUID());
            default -> Map.of("result_id", "res_" + UUID.randomUUID());
        };
        action.setResult(result);
        action.setStatus("executed");
        return toolActionRequestRepository.save(action);
    }

    private boolean isGuardrailFlaggedForDisallowedTool(String ticketId, String toolName) {
        if (!"issue_coupon".equals(toolName)) {
            return false;
        }
        List<AgentRunTrace> traces = agentRunTraceRepository.findAll().stream()
            .filter(t -> ticketId.equals(t.getTicketId()))
            .sorted(Comparator.comparing(AgentRunTrace::getCreatedAt).reversed())
            .toList();
        if (traces.isEmpty()) {
            return false;
        }
        Object flagged = traces.get(0).getGuardrailResults().get("flagged");
        return Boolean.TRUE.equals(flagged);
    }
}
