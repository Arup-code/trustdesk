# TrustDesk Phase 1: Core Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is the detailed sub-plan for Phase 1 of `docs/superpowers/plans/2026-07-22-trustdesk-implementation.md` — read that file's "Global Constraints" and "Repository Layout" sections first for project-wide context.

**Goal:** Stand up the Java Core Service's data model, seed loader, demo JWT auth, and ticket read APIs — the foundation every later phase (triage, drafts, tool actions, evals, frontend) builds on.

**Architecture:** Spring Boot 3.3.7 / Java 21 bytecode target (running on locally installed JDK 26 — Spring Boot 4.0.7 as originally scaffolded does not resolve, and Lombok's compiler hooks are incompatible with JDK 26, so Task 1 pinned the toolchain down and hand-wrote entity boilerplate instead), Spring Data JPA over MySQL (H2 in tests), `jjwt` for stateless bearer-token auth, Jackson (snake_case) for parsing the capstone's seed JSON files.

**Tech Stack:** `backend/build.gradle.kts` was corrected in Task 1 (see that task's report) — Spring Boot 3.3.7, `sourceCompatibility`/`targetCompatibility` = Java 21, no Lombok (entities use hand-written getters/setters/builders with the exact same fluent API Lombok would have generated — `EntityName.builder()...build()`, boolean fields expose `isX()`). Tasks 2–4 below do not use Lombok, so this correction doesn't ripple further. Later phases (Docker, Phase 5) must target a JDK 21-compatible base image, not JDK 25.

## Global Constraints (inherited from the master plan)

- Preserve seed IDs exactly (`cus_1001`, `ord_5001`, `tkt_9001`, ...).
- `Ticket.expected*` fields are seed-only — no service written in this phase may read them for anything but round-tripping; nothing in Phase 1 computes real triage yet, so this is trivially satisfied, but do not add logic that touches them.
- `ToolActionRequest` needs a DB-level unique constraint on `(tool_name, idempotency_key)` — built now even though the approve/execute flow lands in Phase 4, because retrofitting a unique constraint onto a live table is worse than defining it correctly from the start.
- Tests must not require a running MySQL instance — everything runs against H2 via `backend/src/test/resources/application.yaml`.

---

### Task 1: JSON converters, JPA entities, and repositories

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/persistence/JsonConverters.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Customer.java`
- Modify: `backend/src/main/java/app/dexcode/trustdesk/entities/Customer.java` (currently an empty stub — replace entirely)
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Order.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Ticket.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/DraftReply.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/ToolActionRequest.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Approval.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/AgentRunTrace.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/EvalRun.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/CustomerRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/OrderRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/TicketRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/DraftReplyRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/ToolActionRequestRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/ApprovalRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/AgentRunTraceRepository.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/repositories/EvalRunRepository.java`
- Test: `backend/src/test/java/app/dexcode/trustdesk/entities/EntityPersistenceTest.java`

**Interfaces produced (used by every later task/phase):**
- `Customer{customerId, name, email, tier, country, createdAt, verified, tags: List<String>}`
- `Order{orderId, customerId, status, placedAt, deliveredAt, eligibleReturnUntil, total: BigDecimal, currency, paymentStatus, trackingNumber, items: List<Map<String,Object>>}`
- `Ticket{ticketId, customerId, orderId, channel, subject, body, createdAt, status, expectedCategory, expectedPriority, expectedSentiment, expectedEscalation, expectedActions: List<String>, category, priority, sentiment, shouldEscalate, reasonSummary}`
- `DraftReply{draftId, ticketId, status, body, citations: List<String>, createdAt}`
- `ToolActionRequest{actionId, ticketId, toolName, payload: Map<String,Object>, riskLevel, requiresHumanApproval, status, idempotencyKey, createdAt, result: Map<String,Object>}` — unique `(toolName, idempotencyKey)`.
- `Approval{approvalId, actionId, reviewerId, decision, reason, createdAt}`
- `AgentRunTrace{runId, ticketId, runType, status, retrievedDocIds: List<String>, toolCalls: List<Map<String,Object>>, guardrailResults: Map<String,Object>, createdAt}`
- `EvalRun{evalRunId, startedAt, completedAt, totalCases, metrics: Map<String,Object>, caseResults: List<Map<String,Object>>}`
- All repositories are plain `JpaRepository<Entity, String>`, except `ToolActionRequestRepository` which adds `Optional<ToolActionRequest> findByToolNameAndIdempotencyKey(String toolName, String idempotencyKey)`.

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/app/dexcode/trustdesk/entities/EntityPersistenceTest.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EntityPersistenceTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private ToolActionRequestRepository toolActionRequestRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private AgentRunTraceRepository agentRunTraceRepository;
    @Autowired private EvalRunRepository evalRunRepository;

    @Test
    void savesAndReloadsCustomerWithTags() {
        customerRepository.saveAndFlush(Customer.builder()
            .customerId("cus_1001").name("Asha Rao").email("asha@example.com")
            .tier("gold").country("IN").createdAt(Instant.parse("2025-01-01T00:00:00Z"))
            .verified(true).tags(List.of("vip", "beta")).build());

        Customer reloaded = customerRepository.findById("cus_1001").orElseThrow();
        assertEquals(List.of("vip", "beta"), reloaded.getTags());
    }

    @Test
    void savesAndReloadsOrderWithItems() {
        orderRepository.saveAndFlush(Order.builder()
            .orderId("ord_5001").customerId("cus_1001").status("delivered")
            .placedAt(Instant.parse("2025-01-05T00:00:00Z"))
            .deliveredAt(Instant.parse("2025-01-10T00:00:00Z"))
            .eligibleReturnUntil(Instant.parse("2025-02-10T00:00:00Z"))
            .total(new java.math.BigDecimal("49.99")).currency("INR")
            .paymentStatus("paid").trackingNumber("TRK123")
            .items(List.of(Map.of("sku", "BG-AIRPODS-01", "qty", 1))).build());

        Order reloaded = orderRepository.findById("ord_5001").orElseThrow();
        assertEquals("BG-AIRPODS-01", reloaded.getItems().get(0).get("sku"));
    }

    @Test
    void savesAndReloadsTicketWithExpectedAndRealFields() {
        ticketRepository.saveAndFlush(Ticket.builder()
            .ticketId("tkt_9001").customerId("cus_1001").orderId("ord_5001")
            .channel("email").subject("Damaged earbuds")
            .body("My BlueBuds Air arrived with the left earbud cracked.")
            .createdAt(Instant.parse("2025-02-01T00:00:00Z")).status("open")
            .expectedCategory("refund").expectedPriority("medium")
            .expectedEscalation(false)
            .expectedActions(List.of("create_replacement_order")).build());

        Ticket reloaded = ticketRepository.findById("tkt_9001").orElseThrow();
        assertEquals(List.of("create_replacement_order"), reloaded.getExpectedActions());
        assertNull(reloaded.getCategory());
    }

    @Test
    void enforcesIdempotencyUniqueConstraintOnToolActionRequest() {
        toolActionRequestRepository.saveAndFlush(ToolActionRequest.builder()
            .actionId("act_1").ticketId("tkt_9001").toolName("create_replacement_order")
            .payload(Map.of("order_id", "ord_5001")).riskLevel("medium")
            .requiresHumanApproval(true).status("approval_required")
            .idempotencyKey("tkt_9001-replacement-1").createdAt(Instant.now()).build());

        ToolActionRequest duplicate = ToolActionRequest.builder()
            .actionId("act_2").ticketId("tkt_9001").toolName("create_replacement_order")
            .payload(Map.of("order_id", "ord_5001")).riskLevel("medium")
            .requiresHumanApproval(true).status("approval_required")
            .idempotencyKey("tkt_9001-replacement-1").createdAt(Instant.now()).build();

        assertThrows(DataIntegrityViolationException.class,
            () -> toolActionRequestRepository.saveAndFlush(duplicate));
    }

    @Test
    void savesAndReloadsApprovalTraceAndEvalRun() {
        approvalRepository.saveAndFlush(Approval.builder()
            .approvalId("appr_1").actionId("act_1").reviewerId("manager1")
            .decision("approved").reason("Within return window")
            .createdAt(Instant.now()).build());
        assertTrue(approvalRepository.findById("appr_1").isPresent());

        agentRunTraceRepository.saveAndFlush(AgentRunTrace.builder()
            .runId("run_1").ticketId("tkt_9001").runType("triage").status("completed")
            .retrievedDocIds(List.of("KB-REFUND-001"))
            .toolCalls(List.of(Map.of("tool_name", "create_replacement_order")))
            .guardrailResults(Map.of("flagged", false)).createdAt(Instant.now()).build());
        AgentRunTrace reloadedTrace = agentRunTraceRepository.findById("run_1").orElseThrow();
        assertEquals(List.of("KB-REFUND-001"), reloadedTrace.getRetrievedDocIds());

        evalRunRepository.saveAndFlush(EvalRun.builder()
            .evalRunId("eval_run_1").startedAt(Instant.now()).completedAt(Instant.now())
            .totalCases(8).metrics(Map.of("triage_accuracy", 0.875))
            .caseResults(List.of(Map.of("case_id", "eval_001", "passed", true))).build());
        assertTrue(evalRunRepository.findById("eval_run_1").isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*EntityPersistenceTest*"`
Expected: FAIL (compilation error — entities/repositories don't exist yet).

- [ ] **Step 3: Write the implementation**

```java
// backend/src/main/java/app/dexcode/trustdesk/persistence/JsonConverters.java
package app.dexcode.trustdesk.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.util.List;
import java.util.Map;

public class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class StringListConverter implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize list to JSON", e);
            }
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to list", e);
            }
        }
    }

    public static class ObjectListConverter implements AttributeConverter<List<Map<String, Object>>, String> {
        @Override
        public String convertToDatabaseColumn(List<Map<String, Object>> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize list to JSON", e);
            }
        }

        @Override
        public List<Map<String, Object>> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return List.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to list", e);
            }
        }
    }

    public static class StringObjectMapConverter implements AttributeConverter<Map<String, Object>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            try {
                return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize map to JSON", e);
            }
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return Map.of();
            try {
                return MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize JSON to map", e);
            }
        }
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/Customer.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    private String name;
    private String email;
    private String tier;
    private String country;

    @Column(name = "created_at")
    private Instant createdAt;

    private boolean verified;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/Order.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "customer_id")
    private String customerId;

    private String status;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "eligible_return_until")
    private Instant eligibleReturnUntil;

    private BigDecimal total;
    private String currency;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> items;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/Ticket.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    // Seed-only. Read exclusively by the ai-service eval scorer in Phase 5 —
    // never by production triage/draft logic.
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

    // Real triage output — written by TicketService.runTriage() in Phase 2.
    private String category;
    private String priority;
    private String sentiment;

    @Column(name = "should_escalate")
    private Boolean shouldEscalate;

    @Column(name = "reason_summary")
    private String reasonSummary;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/DraftReply.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "draft_replies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/ToolActionRequest.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(
    name = "tool_action_requests",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_tool_action_idempotency",
        columnNames = {"tool_name", "idempotency_key"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolActionRequest {

    @Id
    @Column(name = "action_id")
    private String actionId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "tool_name")
    private String toolName;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> payload;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_human_approval")
    private boolean requiresHumanApproval;

    private String status;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at")
    private Instant createdAt;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> result;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/Approval.java
package app.dexcode.trustdesk.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/AgentRunTrace.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "agent_run_traces")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRunTrace {

    @Id
    @Column(name = "run_id")
    private String runId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "run_type")
    private String runType;

    private String status;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(name = "retrieved_doc_ids", columnDefinition = "TEXT")
    private List<String> retrievedDocIds;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private List<Map<String, Object>> toolCalls;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(name = "guardrail_results", columnDefinition = "TEXT")
    private Map<String, Object> guardrailResults;

    @Column(name = "created_at")
    private Instant createdAt;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/entities/EvalRun.java
package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "eval_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvalRun {

    @Id
    @Column(name = "eval_run_id")
    private String evalRunId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_cases")
    private int totalCases;

    @Convert(converter = JsonConverters.StringObjectMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metrics;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(name = "case_results", columnDefinition = "TEXT")
    private List<Map<String, Object>> caseResults;
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/CustomerRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/OrderRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/TicketRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/DraftReplyRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.DraftReply;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftReplyRepository extends JpaRepository<DraftReply, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/ToolActionRequestRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.ToolActionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ToolActionRequestRepository extends JpaRepository<ToolActionRequest, String> {
    Optional<ToolActionRequest> findByToolNameAndIdempotencyKey(String toolName, String idempotencyKey);
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/ApprovalRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/AgentRunTraceRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.AgentRunTrace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunTraceRepository extends JpaRepository<AgentRunTrace, String> {}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/repositories/EvalRunRepository.java
package app.dexcode.trustdesk.repositories;

import app.dexcode.trustdesk.entities.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, String> {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*EntityPersistenceTest*"`
Expected: PASS (5 tests, including the unique-constraint violation test).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/app/dexcode/trustdesk/persistence backend/src/main/java/app/dexcode/trustdesk/entities backend/src/main/java/app/dexcode/trustdesk/repositories backend/src/test/java/app/dexcode/trustdesk/entities
git commit -m "feat: add core JPA entities, JSON converters, and repositories"
```

---

### Task 2: Test datasource profile + seed data loader

**Files:**
- Create: `backend/src/test/resources/application.yaml`
- Modify: `backend/src/main/resources/application.yaml`
- Create: `backend/src/main/java/app/dexcode/trustdesk/config/DataSeeder.java`
- Test: `backend/src/test/java/app/dexcode/trustdesk/config/SeederIntegrationTest.java`

**Interfaces produced:**
- `DataSeeder.run(ApplicationArguments args)` — public method, callable directly from tests to re-trigger seeding.
- Config property `app.seed.data-dir` (default `../data`, i.e. the repo-root `data/` directory as a sibling of `backend/`).

- [ ] **Step 1: Add the test datasource profile (no test yet — this is fixture setup the next test depends on)**

```yaml
# backend/src/test/resources/application.yaml
spring:
  application:
    name: backend
  datasource:
    url: jdbc:h2:mem:trustdesk-test;MODE=MySQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false

app:
  seed:
    data-dir: does-not-exist
  jwt:
    secret: test-secret-test-secret-test-secret-value
```

- [ ] **Step 2: Write the failing test**

```java
// backend/src/test/java/app/dexcode/trustdesk/config/SeederIntegrationTest.java
package app.dexcode.trustdesk.config;

import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeederIntegrationTest {

    @TempDir
    static Path tempDir;

    @Autowired private CustomerRepository customerRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private DataSeeder dataSeeder;

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
    void seedsCustomersOrdersAndTicketsExactlyOnce() {
        assertEquals(1, customerRepository.count());
        assertEquals(1, orderRepository.count());
        assertEquals(1, ticketRepository.count());
    }

    @Test
    void reRunningSeederDoesNotDuplicateRows() throws Exception {
        dataSeeder.run(null);
        dataSeeder.run(null);
        assertEquals(1, customerRepository.count());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*SeederIntegrationTest*"`
Expected: FAIL (`DataSeeder` class doesn't exist yet).

- [ ] **Step 4: Write the implementation**

```yaml
# backend/src/main/resources/application.yaml — replace entirely
spring:
  application:
    name: backend
  datasource:
    url: jdbc:mysql://localhost:3306/trustdesk
    username: trustdesk
    password: trustdesk
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false

app:
  seed:
    data-dir: ../data
  jwt:
    secret: ${APP_JWT_SECRET:dev-secret-change-me-please-override-in-prod}
    expiration-minutes: 480
  ai-service:
    base-url: ${APP_AI_SERVICE_BASE_URL:http://localhost:8000}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/config/DataSeeder.java
package app.dexcode.trustdesk.config;

import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final String dataDir;
    private final ObjectMapper mapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public DataSeeder(
        CustomerRepository customerRepository,
        OrderRepository orderRepository,
        TicketRepository ticketRepository,
        @Value("${app.seed.data-dir:../data}") String dataDir
    ) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.dataDir = dataDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File customersFile = new File(dataDir, "customers.json");
        if (customerRepository.count() == 0 && customersFile.exists()) {
            for (SeedCustomer c : mapper.readValue(customersFile, SeedCustomer[].class)) {
                customerRepository.save(Customer.builder()
                    .customerId(c.customerId()).name(c.name()).email(c.email())
                    .tier(c.tier()).country(c.country()).createdAt(c.createdAt())
                    .verified(c.verified())
                    .tags(c.tags() == null ? List.of() : c.tags()).build());
            }
        }

        File ordersFile = new File(dataDir, "orders.json");
        if (orderRepository.count() == 0 && ordersFile.exists()) {
            for (SeedOrder o : mapper.readValue(ordersFile, SeedOrder[].class)) {
                orderRepository.save(Order.builder()
                    .orderId(o.orderId()).customerId(o.customerId()).status(o.status())
                    .placedAt(o.placedAt()).deliveredAt(o.deliveredAt())
                    .eligibleReturnUntil(o.eligibleReturnUntil()).total(o.total())
                    .currency(o.currency()).paymentStatus(o.paymentStatus())
                    .trackingNumber(o.trackingNumber())
                    .items(o.items() == null ? List.of() : o.items()).build());
            }
        }

        File ticketsFile = new File(dataDir, "tickets.json");
        if (ticketRepository.count() == 0 && ticketsFile.exists()) {
            for (SeedTicket t : mapper.readValue(ticketsFile, SeedTicket[].class)) {
                ticketRepository.save(Ticket.builder()
                    .ticketId(t.ticketId()).customerId(t.customerId()).orderId(t.orderId())
                    .channel(t.channel()).subject(t.subject()).body(t.body())
                    .createdAt(t.createdAt()).status(t.status())
                    .expectedCategory(t.expectedCategory()).expectedPriority(t.expectedPriority())
                    .expectedSentiment(t.expectedSentiment()).expectedEscalation(t.expectedEscalation())
                    .expectedActions(t.expectedActions() == null ? List.of() : t.expectedActions())
                    .build());
            }
        }
    }

    private record SeedCustomer(
        String customerId, String name, String email, String tier, String country,
        Instant createdAt, boolean verified, List<String> tags) {}

    private record SeedOrder(
        String orderId, String customerId, String status, Instant placedAt,
        Instant deliveredAt, Instant eligibleReturnUntil, BigDecimal total,
        String currency, String paymentStatus, String trackingNumber,
        List<Map<String, Object>> items) {}

    private record SeedTicket(
        String ticketId, String customerId, String orderId, String channel,
        String subject, String body, Instant createdAt, String status,
        String expectedCategory, String expectedPriority, String expectedSentiment,
        Boolean expectedEscalation, List<String> expectedActions) {}
}
```

Note: `File.exists()` guards are load-bearing for `SeederIntegrationTest`'s default (non-`@DynamicPropertySource`) case and for any environment where `data/` hasn't been mounted yet — seeding silently no-ops instead of crashing the app on startup.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*SeederIntegrationTest*"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/resources/application.yaml backend/src/main/resources/application.yaml backend/src/main/java/app/dexcode/trustdesk/config/DataSeeder.java backend/src/test/java/app/dexcode/trustdesk/config/SeederIntegrationTest.java
git commit -m "feat: load seed customers/orders/tickets on startup"
```

---

### Task 3: Demo JWT login + bearer-token auth filter

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/DemoUsersConfig.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/JwtService.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/JwtAuthFilter.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/SecurityConfig.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/AuthController.java`
- Test: `backend/src/test/java/app/dexcode/trustdesk/controllers/AuthControllerTest.java`

**Interfaces produced (used by Task 4 and every later phase's tests):**
- `POST /auth/login` body `{username, password}` → `200 {token, username, role}` or `401`.
- Demo users: `agent1` / `agent123` (role `support_agent`), `manager1` / `manager123` (role `support_manager`).
- Every other route requires `Authorization: Bearer <token>` or returns `401`.

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/app/dexcode/trustdesk/controllers/AuthControllerTest.java
package app.dexcode.trustdesk.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new AuthController.LoginRequest("agent1", "agent123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.role").value("support_agent"));
    }

    @Test
    void loginWithInvalidCredentialsReturns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new AuthController.LoginRequest("agent1", "wrong-password"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/tickets"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*AuthControllerTest*"`
Expected: FAIL (`AuthController` doesn't exist yet).

- [ ] **Step 3: Write the implementation**

```java
// backend/src/main/java/app/dexcode/trustdesk/security/DemoUsersConfig.java
package app.dexcode.trustdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class DemoUsersConfig {

    public record DemoUser(String username, String passwordHash, String role) {}

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Map<String, DemoUser> demoUsers(PasswordEncoder passwordEncoder) {
        return Map.of(
            "agent1", new DemoUser("agent1", passwordEncoder.encode("agent123"), "support_agent"),
            "manager1", new DemoUser("manager1", passwordEncoder.encode("manager123"), "support_manager")
        );
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/security/JwtService.java
package app.dexcode.trustdesk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiration)))
            .signWith(key)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/security/JwtAuthFilter.java
package app.dexcode.trustdesk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                Claims claims = jwtService.parseClaims(token);
                String role = claims.get("role", String.class);
                var authToken = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/security/SecurityConfig.java
package app.dexcode.trustdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/actuator/health",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/controllers/AuthController.java
package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.security.DemoUsersConfig;
import app.dexcode.trustdesk.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final Map<String, DemoUsersConfig.DemoUser> demoUsers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
        Map<String, DemoUsersConfig.DemoUser> demoUsers,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.demoUsers = demoUsers;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, String username, String role) {}

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        DemoUsersConfig.DemoUser user = demoUsers.get(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            return ResponseEntity.status(401).build();
        }
        String token = jwtService.issueToken(user.username(), user.role());
        return ResponseEntity.ok(new LoginResponse(token, user.username(), user.role()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*AuthControllerTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/app/dexcode/trustdesk/security backend/src/main/java/app/dexcode/trustdesk/controllers/AuthController.java backend/src/test/java/app/dexcode/trustdesk/controllers/AuthControllerTest.java
git commit -m "feat: add demo JWT login and bearer-token auth filter"
```

---

### Task 4: Ticket list/detail APIs with customer and order context

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/dto/TicketDetailResponse.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/services/TicketService.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/TicketController.java`
- Test: `backend/src/test/java/app/dexcode/trustdesk/controllers/TicketControllerTest.java`

**Interfaces produced (used by Phase 2's `TriageController` and Phase 3's `DraftController`):**
- `TicketService.listTickets() -> List<Ticket>`
- `TicketService.getTicketDetail(String ticketId) -> TicketDetailResponse` — throws `NoSuchElementException` if not found.
- `GET /tickets -> 200 [Ticket...]`
- `GET /tickets/{id} -> 200 TicketDetailResponse | 404`

- [ ] **Step 1: Write the failing test**

```java
// backend/src/test/java/app/dexcode/trustdesk/controllers/TicketControllerTest.java
package app.dexcode.trustdesk.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.data-dir=../data")
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new AuthController.LoginRequest("agent1", "agent123"))))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    @Test
    void listsSeededTickets() throws Exception {
        mockMvc.perform(authed(get("/tickets")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.ticketId == 'tkt_9001')]").exists());
    }

    @Test
    void fetchesTicketDetailWithCustomerAndOrder() throws Exception {
        mockMvc.perform(authed(get("/tickets/tkt_9001")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customer.customerId").value("cus_1001"))
            .andExpect(jsonPath("$.order.orderId").value("ord_5001"));
    }

    @Test
    void returns404ForUnknownTicket() throws Exception {
        mockMvc.perform(authed(get("/tickets/does-not-exist")))
            .andExpect(status().isNotFound());
    }
}
```

This test depends on the real seed pack (`data/customers.json`, `data/orders.json`, `data/tickets.json`, containing `cus_1001` / `ord_5001` / `tkt_9001`) being present at the repo root — copied there in Phase 0. If those files aren't present yet, copy them from the capstone pack before running this task.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "*TicketControllerTest*"`
Expected: FAIL (`TicketController` doesn't exist yet).

- [ ] **Step 3: Write the implementation**

```java
// backend/src/main/java/app/dexcode/trustdesk/dto/TicketDetailResponse.java
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
```

```java
// backend/src/main/java/app/dexcode/trustdesk/services/TicketService.java
package app.dexcode.trustdesk.services;

import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.entities.Customer;
import app.dexcode.trustdesk.entities.Order;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.repositories.CustomerRepository;
import app.dexcode.trustdesk.repositories.OrderRepository;
import app.dexcode.trustdesk.repositories.TicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public TicketService(
        TicketRepository ticketRepository,
        CustomerRepository customerRepository,
        OrderRepository orderRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    public List<Ticket> listTickets() {
        return ticketRepository.findAll();
    }

    public TicketDetailResponse getTicketDetail(String ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
        Customer customer = ticket.getCustomerId() == null ? null :
            customerRepository.findById(ticket.getCustomerId()).orElse(null);
        Order order = ticket.getOrderId() == null ? null :
            orderRepository.findById(ticket.getOrderId()).orElse(null);
        return TicketDetailResponse.from(ticket, customer, order);
    }
}
```

```java
// backend/src/main/java/app/dexcode/trustdesk/controllers/TicketController.java
package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.TicketDetailResponse;
import app.dexcode.trustdesk.entities.Ticket;
import app.dexcode.trustdesk.services.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<Ticket> listTickets() {
        return ticketService.listTickets();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponse> getTicket(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ticketService.getTicketDetail(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "*TicketControllerTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full backend test suite to confirm nothing regressed**

Run: `cd backend && ./gradlew test`
Expected: PASS (all tests across Tasks 1–4).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/app/dexcode/trustdesk/dto backend/src/main/java/app/dexcode/trustdesk/services backend/src/main/java/app/dexcode/trustdesk/controllers/TicketController.java backend/src/test/java/app/dexcode/trustdesk/controllers/TicketControllerTest.java
git commit -m "feat: add ticket list/detail APIs with customer/order context"
```

---

## Phase 1 Acceptance Criteria

- [ ] `./gradlew test` passes with no failures across all four tasks.
- [ ] Data from `data/customers.json`, `data/orders.json`, `data/tickets.json` loads on startup and is idempotent across restarts.
- [ ] `POST /auth/login` issues a bearer token for the two demo users; every other route requires it.
- [ ] `GET /tickets` and `GET /tickets/{id}` work end-to-end against the real seed pack, with `GET /tickets/tkt_9001` returning nested `cus_1001` / `ord_5001` context.
- [ ] The `(tool_name, idempotency_key)` unique constraint exists and is proven by a passing test, ready for Phase 4 to build on without schema changes.

## Deferred (explicitly out of scope for Phase 1)

- `POST /tickets` (ticket creation) — capstone Must Have explicitly allows relying on seed tickets only; add later as a Good-to-Have if time permits.
- Role-based `@PreAuthorize` enforcement — the `role` claim is already in the JWT (Task 3), enforcement is deferred to the Phase 5 Good-to-Have follow-on per the master plan.
