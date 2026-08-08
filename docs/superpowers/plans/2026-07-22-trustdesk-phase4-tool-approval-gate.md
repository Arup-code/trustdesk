# TrustDesk Phase 4: Tool Registry, Approval Gate, Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is the detailed sub-plan for Phase 4 of `docs/superpowers/plans/2026-07-22-trustdesk-implementation.md` — read that file's "Global Constraints" section first.

**Goal:** Load the real tool catalog (`data/tool_actions.json`), validate and idempotently persist tool-action requests, gate execution behind human approval, and add a guardrail-based denial layer so a flagged ticket can never get `issue_coupon` (or any other tool) through the request gate even if something upstream misbehaves.

**Architecture:** Pure Java, no Python changes. `ToolCatalog` loads `data/tool_actions.json` once at startup into an in-memory map (same `app.seed.data-dir` config Phase 1's `DataSeeder` already uses). `ToolActionService` is the single place that validates a request against the catalog, enforces idempotency at the database level (unique `(tool_name, idempotency_key)` constraint, already created in Phase 1), and drives the `requested → approval_required → approved/rejected → executed` lifecycle. `ToolActionController` is a thin HTTP wrapper mapping service-layer exceptions to the right status codes.

**Tech Stack:** No new dependencies — Spring Data JPA, Jackson (already used for `DataSeeder`'s snake_case parsing).

## Global Constraints (inherited from the master plan)

- Preserve seed IDs exactly.
- No sensitive tool action may execute without an explicit human approval step recorded in the `Approval` table first — `execute()` must reject anything not in `approved` status.
- Idempotency: the unique DB constraint on `(tool_name, idempotency_key)` already exists (Phase 1) — a retry with the same key must return the existing `ToolActionRequest` row, never create a new one, never error.
- The one Must-Have approval-gated action is `create_replacement_order`.
- `escalate_to_human` (in the real catalog, `requires_human_approval: false`) must never be used as if it required approval — this plan doesn't special-case it, the catalog data itself already encodes `requires_human_approval: false` for it, so `requestAction` will naturally route it straight to `requested` status rather than `approval_required`.
- `Ticket.expected*` fields must never be read anywhere in this diff.

---

### Task 4.1: Tool catalog, approval gate, idempotent request/execute

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/config/ToolCatalog.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java`
- Test: `backend/src/test/java/app/dexcode/trustdesk/services/ToolActionServiceTest.java`

**Interfaces produced (used by Task 4.2):**
- `ToolCatalog.find(String toolName) -> Optional<ToolCatalog.ToolDefinition>` — `ToolDefinition(toolName, description, riskLevel, requiresHumanApproval, allowedCategories, requiredFields, maxAmountInr)`.
- `ToolActionService.requestAction(String ticketId, String toolName, Map<String, Object> payload) -> ToolActionRequest` — throws `ToolActionService.ToolActionValidationException` (unknown tool / missing field / disallowed category) or `java.util.NoSuchElementException` (unknown ticket).
- `ToolActionService.approve(String actionId, String reviewerId, String decision, String reason) -> Approval` — throws `NoSuchElementException` or `ToolActionService.InvalidToolActionStateException`.
- `ToolActionService.execute(String actionId) -> ToolActionRequest` — throws `NoSuchElementException` or `ToolActionService.InvalidToolActionStateException`; idempotent on an already-`executed` row (returns the cached result, doesn't re-execute).
- `POST /tool-actions` (body `{"ticket_id", "tool_name", "payload": {...}}`) → `201` or `400`.
- `POST /tool-actions/{id}/approve` (body `{"reviewer_id", "decision", "reason"}`) → `200`, `404`, or `409`.
- `POST /tool-actions/{id}/execute` → `200`, `404`, or `409`.

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/app/dexcode/trustdesk/services/ToolActionServiceTest.java
package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = "app.seed.data-dir=../data")
class ToolActionServiceTest {

    @Autowired private ToolActionService toolActionService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;

    private void setTicketCategory(String ticketId, String category) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        ticket.setCategory(category);
        ticketRepository.save(ticket);
    }

    @Test
    void requestActionRejectsUnknownTool() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "not_a_real_tool", Map.of("idempotency_key", "k1")));
        assertTrue(ex.getMessage().contains("not_a_real_tool"));
    }

    @Test
    void requestActionRejectsMissingRequiredField() {
        setTicketCategory("tkt_9001", "refund");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
                "order_id", "ord_5001", "idempotency_key", "k2")));
        assertNotNull(ex.getMessage());
    }

    @Test
    void requestActionRejectsDisallowedCategory() {
        setTicketCategory("tkt_9002", "shipping");
        var ex = assertThrows(ToolActionService.ToolActionValidationException.class, () ->
            toolActionService.requestAction("tkt_9002", "create_replacement_order", Map.of(
                "order_id", "ord_5002", "sku", "BG-CASE-14", "reason", "damaged",
                "idempotency_key", "k3")));
        assertTrue(ex.getMessage().contains("shipping"));
    }

    @Test
    void requestActionIsIdempotentOnRetry() {
        setTicketCategory("tkt_9001", "refund");
        Map<String, Object> payload = Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-retry-test");

        ToolActionRequest first = toolActionService.requestAction("tkt_9001", "create_replacement_order", payload);
        ToolActionRequest second = toolActionService.requestAction("tkt_9001", "create_replacement_order", payload);

        assertEquals(first.getActionId(), second.getActionId());
        long count = toolActionRequestRepository.findAll().stream()
            .filter(a -> "tkt_9001-replacement-retry-test".equals(a.getIdempotencyKey()))
            .count();
        assertEquals(1, count);
    }

    @Test
    void executeBeforeApproveIsRejected() {
        setTicketCategory("tkt_9001", "refund");
        ToolActionRequest action = toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-execute-before-approve"));

        assertThrows(ToolActionService.InvalidToolActionStateException.class, () ->
            toolActionService.execute(action.getActionId()));
    }

    @Test
    void fullHappyPathRequestApproveExecute() {
        setTicketCategory("tkt_9001", "refund");
        ToolActionRequest requested = toolActionService.requestAction("tkt_9001", "create_replacement_order", Map.of(
            "order_id", "ord_5001", "sku", "BG-AIRPODS-01", "reason", "damaged",
            "idempotency_key", "tkt_9001-replacement-happy-path"));
        assertEquals("approval_required", requested.getStatus());

        Approval approval = toolActionService.approve(
            requested.getActionId(), "manager1", "approved", "Within policy");
        assertEquals("approved", approval.getDecision());

        ToolActionRequest executed = toolActionService.execute(requested.getActionId());
        assertEquals("executed", executed.getStatus());
        assertNotNull(executed.getResult());
        assertTrue(executed.getResult().containsKey("replacement_order_id"));

        ToolActionRequest reExecuted = toolActionService.execute(requested.getActionId());
        assertEquals(
            executed.getResult().get("replacement_order_id"),
            reExecuted.getResult().get("replacement_order_id"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*ToolActionServiceTest*"`
Expected: FAIL (`ToolActionService`, `ToolCatalog` don't exist yet).

- [ ] **Step 3: Write the implementation**

```java
// backend/src/main/java/app/dexcode/trustdesk/config/ToolCatalog.java
package app.dexcode.trustdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ToolCatalog {

    private final Map<String, ToolDefinition> definitions;

    public ToolCatalog(@Value("${app.seed.data-dir:../data}") String dataDir) {
        ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        try {
            ToolDefinition[] catalog = mapper.readValue(
                new File(dataDir, "tool_actions.json"), ToolDefinition[].class);
            this.definitions = Arrays.stream(catalog)
                .collect(Collectors.toMap(ToolDefinition::toolName, d -> d));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load tool catalog from " + dataDir, e);
        }
    }

    public Optional<ToolDefinition> find(String toolName) {
        return Optional.ofNullable(definitions.get(toolName));
    }

    public record ToolDefinition(
        String toolName,
        String description,
        String riskLevel,
        boolean requiresHumanApproval,
        List<String> allowedCategories,
        List<String> requiredFields,
        Integer maxAmountInr
    ) {}
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java
package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.config.ToolCatalog;
import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.repositories.ApprovalRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import app.dexcode.trustdesk.repositories.ToolActionRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ToolActionService {

    private final ToolCatalog toolCatalog;
    private final ToolActionRequestRepository toolActionRequestRepository;
    private final TicketRepository ticketRepository;
    private final ApprovalRepository approvalRepository;

    public ToolActionService(
        ToolCatalog toolCatalog,
        ToolActionRequestRepository toolActionRequestRepository,
        TicketRepository ticketRepository,
        ApprovalRepository approvalRepository
    ) {
        this.toolCatalog = toolCatalog;
        this.toolActionRequestRepository = toolActionRequestRepository;
        this.ticketRepository = ticketRepository;
        this.approvalRepository = approvalRepository;
    }

    public static class ToolActionValidationException extends RuntimeException {
        public ToolActionValidationException(String message) { super(message); }
    }

    public static class InvalidToolActionStateException extends RuntimeException {
        public InvalidToolActionStateException(String message) { super(message); }
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
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java
package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.entities.Approval;
import app.dexcode.trustdesk.entities.ToolActionRequest;
import app.dexcode.trustdesk.services.ToolActionService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/tool-actions")
public class ToolActionController {

    private final ToolActionService toolActionService;

    public ToolActionController(ToolActionService toolActionService) {
        this.toolActionService = toolActionService;
    }

    public record RequestActionBody(
        @JsonProperty("ticket_id") String ticketId,
        @JsonProperty("tool_name") String toolName,
        Map<String, Object> payload
    ) {}

    public record ApproveBody(
        @JsonProperty("reviewer_id") String reviewerId,
        String decision,
        String reason
    ) {}

    @PostMapping
    public ResponseEntity<?> requestAction(@RequestBody RequestActionBody body) {
        try {
            ToolActionRequest action =
                toolActionService.requestAction(body.ticketId(), body.toolName(), body.payload());
            return ResponseEntity.status(HttpStatus.CREATED).body(action);
        } catch (ToolActionService.ToolActionValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestBody ApproveBody body) {
        try {
            Approval approval =
                toolActionService.approve(id, body.reviewerId(), body.decision(), body.reason());
            return ResponseEntity.ok(approval);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (ToolActionService.InvalidToolActionStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> execute(@PathVariable String id) {
        try {
            ToolActionRequest action = toolActionService.execute(id);
            return ResponseEntity.ok(action);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (ToolActionService.InvalidToolActionStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
```

Note: `SecurityConfig` needs no change — `/tool-actions/**` isn't in the `permitAll()` allowlist, so it already falls under `.anyRequest().authenticated()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*ToolActionServiceTest*"`
Expected: PASS (6 tests).

- [ ] **Step 5: Run the full backend suite to confirm nothing regressed**

Run: `cd backend && ./gradlew test`
Expected: PASS (all tests from Phases 1–3 plus this task).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/app/dexcode/trustdesk/config/ToolCatalog.java backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java backend/src/test/java/app/dexcode/trustdesk/services/ToolActionServiceTest.java
git commit -m "feat: add tool-action catalog, approval gate, and idempotent request/execute"
```

---

### Task 4.2: Guardrail-based tool-action denial at the request gate

**Files:**
- Modify: `backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java` (add the guardrail check to `requestAction`, add `ToolActionDeniedException`, add the `AgentRunTraceRepository` dependency)
- Modify: `backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java` (map the new exception to `403`)
- Modify: `backend/src/test/java/app/dexcode/trustdesk/services/ToolActionServiceTest.java` (add the denial test)

**Rationale:** Phase 3's draft graph already stops the AI from *recommending* `issue_coupon` on a flagged ticket, but nothing before this task stops a human agent (or a buggy client) from directly calling `POST /tool-actions` with `tool_name: "issue_coupon"` against a ticket whose most recent `AgentRunTrace` shows a coupon-injection guardrail flag. This is the actual enforcement point the capstone's `eval_006` ("do not issue coupon") depends on holding even if something upstream misbehaves.

**Interfaces produced:**
- `ToolActionService.ToolActionDeniedException` — new exception type, mapped to `403` by the controller.
- `POST /tool-actions` now also returns `403` when the ticket's latest `AgentRunTrace.guardrailResults` shows `flagged=true` and the requested tool is `issue_coupon`.

- [ ] **Step 1: Write the failing test**

Add to `ToolActionServiceTest.java`:

```java
    @Test
    void requestActionDeniedWhenTicketHasFlaggedGuardrailTraceAndToolIsIssueCoupon() {
        setTicketCategory("tkt_9006", "general");
        agentRunTraceRepository.save(app.dexcode.trustdesk.entities.AgentRunTrace.builder()
            .runId(java.util.UUID.randomUUID().toString())
            .ticketId("tkt_9006")
            .runType("draft_reply")
            .status("completed")
            .retrievedDocIds(java.util.List.of())
            .toolCalls(java.util.List.of())
            .guardrailResults(Map.of("flagged", true, "category", "coupon_injection"))
            .createdAt(java.time.Instant.now())
            .build());

        var ex = assertThrows(ToolActionService.ToolActionDeniedException.class, () ->
            toolActionService.requestAction("tkt_9006", "issue_coupon", Map.of(
                "customer_id", "cus_1006", "amount", 500, "reason", "goodwill",
                "idempotency_key", "tkt_9006-coupon-denied")));
        assertTrue(ex.getMessage().contains("issue_coupon"));
    }
```

Also add the field to the test class:

```java
    @Autowired private app.dexcode.trustdesk.repositories.AgentRunTraceRepository agentRunTraceRepository;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*ToolActionServiceTest*"`
Expected: FAIL (`ToolActionDeniedException` doesn't exist yet; the request currently succeeds instead of being denied).

- [ ] **Step 3: Write the implementation**

Modify `ToolActionService.java`:

```java
// add these imports
import app.dexcode.trustdesk.entities.AgentRunTrace;
import app.dexcode.trustdesk.repositories.AgentRunTraceRepository;
import java.util.Comparator;
import java.util.List;

// add the new exception type, next to the existing two
    public static class ToolActionDeniedException extends RuntimeException {
        public ToolActionDeniedException(String message) { super(message); }
    }

// add the new constructor dependency
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

// add the guardrail check inside requestAction(), after the category check and before
// building the ToolActionRequest:
        if (isGuardrailFlaggedForDisallowedTool(ticketId, toolName)) {
            throw new ToolActionDeniedException(
                "Tool " + toolName + " denied: ticket has a flagged guardrail trace");
        }

// add this private helper method
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
```

Modify `ToolActionController.java` — add a catch clause to `requestAction`:

```java
        } catch (ToolActionService.ToolActionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
```

(placed as an additional `catch` block alongside the existing `ToolActionValidationException`/`NoSuchElementException` catches in `requestAction`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*ToolActionServiceTest*"`
Expected: PASS (7 tests).

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS (all tests from Phases 1–4).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java backend/src/test/java/app/dexcode/trustdesk/services/ToolActionServiceTest.java
git commit -m "feat: enforce guardrail-based tool-action denial at the request gate"
```

---

## Phase 4 Acceptance Criteria (mirrors `docs/IMPLEMENTATION_GUIDE.md` Step 6 exactly)

- [ ] AI can recommend `create_replacement_order` (already true since Phase 3); a human approval step is required before execution; status and idempotency key persist; retries don't duplicate.
- [ ] `issue_coupon` cannot be requested for a ticket with a flagged coupon-injection guardrail trace, regardless of who or what calls `POST /tool-actions`.
- [ ] Full backend suite passes.

## Deferred (explicitly out of scope for Phase 4)

- Wiring the frontend to actually call these endpoints — Phase 5.
- Enforcing the guardrail-denial check for tools other than `issue_coupon`, or generalizing it beyond the coupon-injection category — the capstone's Must-Have only requires this to hold for `eval_006`'s specific case; broader enforcement is a Good-to-Have.
- `maxAmountInr` validation on `issue_coupon` payloads — not required by the Must-Have (`create_replacement_order` is the one action that must fully work end-to-end), noted as a Good-to-Have follow-on if `issue_coupon` gets built out further.
