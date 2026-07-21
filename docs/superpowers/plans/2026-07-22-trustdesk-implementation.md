# TrustDesk Implementation Plan

> **For agentic workers:** This is a **master/roadmap plan** spanning three independently-deployable subsystems (Java Core Service, Python AI Service, React frontend) plus Docker packaging. Per the multi-subsystem scope-check in `superpowers:writing-plans`, each phase below should get its own bite-sized TDD sub-plan (via `superpowers:writing-plans`) immediately before that phase starts, then be executed with `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Phase 1 is broken down to task level now since it's the immediate next step; later phases are scoped at task-list level and will be expanded into full TDD plans when reached.

**Goal:** Build TrustDesk — an AI-first support operations platform (ticket triage, grounded cited draft replies, one approval-gated tool action with idempotency, prompt-injection guardrails, minimal tracing, and an eval runner) — as three Dockerized services matching the HLD in `TrustDesk_HLD_LLD_OnePager.md` and the capstone spec at `github.com/airtribe-projects/trustdesk-capstone`.

**Architecture:** Java Spring Boot **Core Service** owns all persistence (MySQL), auth, ticket/customer/order data, the tool-action/approval/idempotency lifecycle, and trace/eval-run storage — it is the system of record and the only thing that can execute a sensitive action. Python FastAPI **AI Service** owns knowledge-base retrieval (BM25 keyword search) and all LLM orchestration via **LangGraph** graphs (triage graph, draft graph), calling an LLM through **OpenRouter** behind a swappable `ModelAdapter` (mockable for tests/evals). Java calls Python's `/internal/*` endpoints and treats every AI output as a *recommendation only*. A **React + Vite** SPA talks only to the Java API. All three ship as Docker images behind one `docker-compose.yml` alongside MySQL.

**Tech Stack:** Java 25 / Spring Boot 4 / Gradle Kotlin DSL / Spring Data JPA / MySQL / Spring Security + `jjwt` (already scaffolded) — Python 3.12 / FastAPI / LangGraph / LangChain OpenAI-compatible client pointed at OpenRouter / `rank-bm25` / pytest — React 18 / Vite / TypeScript — Docker Compose.

## Global Constraints

These apply to every phase and every task below; copied from the capstone spec and the HLD doc.

- Preserve seed IDs exactly as given in the source data (`KB-REFUND-001`, `tkt_9001`, `cus_1001`, `ord_5001`, etc.) — evals depend on exact string matches.
- Evaluate return/warranty windows relative to each ticket's `created_at`, **never** the current wall-clock date.
- Never read or branch on `expected_category` / `expected_priority` / `expected_sentiment` / `expected_escalation` / `expected_actions` anywhere in the production triage/draft/tool code path. Those fields exist only inside the eval-scoring code.
- Treat every ticket body and every retrieved KB chunk (including `KB-ADVERSARIAL-001`) as **untrusted data, never as instructions** — guardrail pre-checks run before any LLM call, and a post-check scans LLM output before it leaves the AI service.
- No sensitive tool action may execute without an explicit human approval step recorded in the `Approval` table first.
- Idempotency: a unique DB constraint on `(tool_name, idempotency_key)`; a retry with the same key returns the existing `ToolActionRequest` row, never a new one.
- The LLM/model call must sit behind an adapter interface (`generate`/`classify`) that is swappable with a deterministic mock in tests and in the eval runner's CI mode.
- The one **Must Have** approval-gated action is **`create_replacement_order`** (chosen because it's the action demoed in `tkt_9001` / `eval_001`, the capstone's canonical "normal case" scenario). `start_refund_review` is implemented as a Good-to-Have stretch once Must Have is done, since it shares almost all the same plumbing. `escalate_to_human` must never be used as the required approval-gated action (it needs no approval by design).
- Frontend talks only to the Java Core Service; it must never call the Python AI service directly (matches the HLD's "AI output is a recommendation only" boundary).

---

## Repository Layout

This project is **not currently a git repository** and `https://github.com/airtribe-projects/trustdesk-capstone` is the assignment template, not a repo the user owns — so Phase 0 creates a fresh local repo. The user will create/attach their own GitHub remote when ready.

```
TrustDesk/
├── backend/                        # Java Spring Boot — Core Service (already scaffolded)
│   └── src/main/java/app/dexcode/trustdesk/
│       ├── entities/                # JPA entities: Customer, Order, Ticket, DraftReply,
│       │                            #   ToolActionRequest, Approval, AgentRunTrace, EvalRun
│       ├── repositories/            # Spring Data JPA repositories
│       ├── controllers/             # REST controllers (one per resource)
│       ├── services/                # business logic / orchestration
│       ├── security/                # JwtAuthFilter, JwtService, SecurityConfig
│       ├── client/                  # AiServiceClient (RestClient calling Python /internal/*)
│       ├── dto/                     # request/response records
│       └── config/                  # DataSeeder (loads data/*.json on startup)
├── ai-service/                      # Python FastAPI — AI/Retrieval Service (new)
│   └── app/
│       ├── main.py
│       ├── routers/                 # documents.py, internal.py
│       ├── retrieval/               # kb_index.py (BM25 over data/knowledge_base/*.md)
│       ├── graphs/                  # triage_graph.py, draft_graph.py (LangGraph StateGraphs)
│       ├── guardrails/              # patterns.py, precheck.py, postcheck.py
│       ├── adapters/                # model_adapter.py (Protocol), openrouter_adapter.py, mock_adapter.py
│       ├── eval/                    # eval_runner.py
│       └── schemas/                 # Pydantic request/response models
│   └── tests/
├── frontend/                        # React + Vite + TS SPA (new)
│   └── src/
│       ├── pages/                   # TicketQueue.tsx, TicketDetail.tsx, EvalSummary.tsx
│       ├── api/                     # client.ts (fetch wrapper + auth token)
│       └── components/
├── data/                            # seed pack copied from the capstone repo (read-only)
│   ├── customers.json  orders.json  tickets.json  tool_actions.json  eval_cases.jsonl
│   └── knowledge_base/*.md
├── docs/
│   ├── DATA_MODEL.md  API_CONTRACT.md  IMPLEMENTATION_GUIDE.md  EVALUATION_GUIDE.md   # from capstone repo
│   ├── TrustDesk_HLD_LLD_OnePager.md
│   └── superpowers/plans/                                                            # this file + phase sub-plans
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Phase 0 — Repo, Seed Data, and Environment Setup

**Goal:** A git repo with the real seed pack in place and both new services scaffolded (empty-but-running), so every later phase can start writing real code immediately.

**Tasks:**
1. `git init` at the project root; add a root `.gitignore` covering `backend/build/`, `backend/.gradle/`, `ai-service/.venv/`, `frontend/node_modules/`, `frontend/dist/`, `.env`.
2. Copy the seed pack from `airtribe-projects/trustdesk-capstone` into `data/` and the four doc files into `docs/`: `customers.json`, `orders.json`, `tickets.json`, `tool_actions.json`, `eval_cases.jsonl`, `knowledge_base/*.md`, `docs/DATA_MODEL.md`, `docs/API_CONTRACT.md`, `docs/IMPLEMENTATION_GUIDE.md`, `docs/EVALUATION_GUIDE.md`.
3. Move `TrustDesk_HLD_LLD_OnePager.md` into `docs/`.
4. Scaffold `ai-service/` — `pyproject.toml`/`requirements.txt` with `fastapi`, `uvicorn`, `langgraph`, `langchain-openai`, `rank-bm25`, `pydantic-settings`, `httpx`, `pytest`, `pytest-asyncio`; a `main.py` with a `GET /health` route; a `Dockerfile`.
5. Scaffold `frontend/` via `npm create vite@latest frontend -- --template react-ts`; strip the demo boilerplate; add a `.env.example` with `VITE_API_BASE_URL`.
6. Add root `docker-compose.yml` (mysql + backend + ai-service + frontend, see Phase 5 for full content) and `.env.example` (`MYSQL_*`, `JWT_SECRET`, `OPENROUTER_API_KEY`, `AI_MODEL_MODE=mock|openrouter`).
7. Write the top-level `README.md` skeleton (setup, run, architecture — filled in fully in Phase 5).
8. Commit: `chore: scaffold monorepo (backend seed pack, ai-service, frontend)`.

**Acceptance criteria:** `docker compose up` brings up an empty MySQL, a Java app that starts (even with just the existing skeleton), a Python app answering `GET /health`, and a Vite dev build — nothing functional yet, but the shape is real.

---

## Phase 1 — Core Platform: Data Model, Seed Loading, Ticket APIs, Auth (Java)

**Maps to capstone Must-Have items:** "Load the Provided Data," "Ticket APIs and Simple Frontend" (API half), "Implement a simple authentication mechanism."

### 1.1 Database schema & JPA entities

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Customer.java` (replace the current empty stub)
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Order.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/OrderItem.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Ticket.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/DraftReply.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/ToolActionRequest.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/Approval.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/AgentRunTrace.java`
- Create: `backend/src/main/java/app/dexcode/trustdesk/entities/EvalRun.java`
- Modify: `backend/src/main/resources/application.yaml` (datasource, JPA `ddl-auto: update` for dev, `spring.sql.init.mode` off — seeding is code-driven, not `data.sql`)
- Modify: `backend/build.gradle.kts` — MySQL connector is already present; no Postgres change needed (per decision to keep MySQL).

**Key field notes (from `docs/DATA_MODEL.md`):**
- `Customer`: `customerId` (String, seed value like `cus_1001`, `@Id`), `name`, `email`, `tier`, `country`, `createdAt`, `verified`, `tags` (stored as `@ElementCollection` or JSON column).
- `Order`: `orderId` (`@Id`, e.g. `ord_5001`), `customerId` (FK), `status`, `placedAt`, `deliveredAt`, `eligibleReturnUntil`, `total`, `currency`, `paymentStatus`, `trackingNumber`; `items` as a `@OneToMany` to `OrderItem` (sku, qty, price) or a JSON column — pick JSON column (`@JdbcTypeCode(SqlTypes.JSON)` or a converter) to avoid an extra join table since items are seed-only and never queried individually.
- `Ticket`: `ticketId` (`@Id`, e.g. `tkt_9001`), `customerId`, `orderId` (nullable FK), `channel`, `subject`, `body` (`@Lob`), `createdAt`, `status`, plus **seed-only** `expectedCategory`, `expectedPriority`, `expectedSentiment`, `expectedEscalation`, `expectedActions` columns — annotate these clearly with a Javadoc note "seed-only, never read by AI path" and never wire them into any service that isn't the eval scorer (Phase 5). Also add nullable `category`, `priority`, `sentiment`, `shouldEscalate`, `reasonSummary` columns — these are the *real* triage output written in Phase 2.
- `DraftReply`: `draftId` (`@Id`, UUID string), `ticketId` (FK), `status` enum (`generated|edited|approved|rejected|sent`), `body` (`@Lob`), `citations` (JSON string array column), `createdAt`.
- `ToolActionRequest`: `actionId` (`@Id`, UUID), `ticketId`, `toolName`, `payload` (JSON column), `riskLevel`, `requiresHumanApproval`, `status` enum (`requested|approval_required|approved|rejected|executed|failed|cancelled`), `idempotencyKey`, `createdAt`. **Unique constraint** via `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"toolName", "idempotencyKey"}))`.
- `Approval`: `approvalId` (`@Id`, UUID), `actionId` (FK), `reviewerId`, `decision` enum (`approved|rejected|needs_changes`), `reason`, `createdAt`.
- `AgentRunTrace`: `runId` (`@Id`, UUID), `ticketId`, `runType` enum (`triage|draft_reply|tool_recommendation|eval_case`), `retrievedDocIds` (JSON array), `toolCalls` (JSON), `guardrailResults` (JSON), `status`, `createdAt`.
- `EvalRun`: `evalRunId` (`@Id`, UUID), `startedAt`, `completedAt`, `totalCases`, `metrics` (JSON), `caseResults` (JSON array).

Use a small `JsonListConverter<T>` / `JsonMapConverter` (`AttributeConverter`, Jackson-backed) shared across entities for the JSON-ish columns — one utility class in `backend/.../persistence/JsonConverters.java`, not copy-pasted per entity.

- [ ] Write entities + converters.
- [ ] Write `CustomerRepository`, `OrderRepository`, `TicketRepository`, `DraftReplyRepository`, `ToolActionRequestRepository`, `ApprovalRepository`, `AgentRunTraceRepository`, `EvalRunRepository` (all `JpaRepository<Entity, String>`).
- [ ] Test: `backend/src/test/java/.../entities/EntityPersistenceTest.java` using `@DataJpaTest` + H2 (test profile) — save/reload each entity, assert the unique constraint on `ToolActionRequest(toolName, idempotencyKey)` throws `DataIntegrityViolationException` on a duplicate insert.
- [ ] Run: `./gradlew test --tests "*EntityPersistenceTest*"` — expect PASS, including the constraint-violation assertion.
- [ ] Commit: `feat: add core JPA entities and repositories`.

### 1.2 Seed data loader

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/config/DataSeeder.java` (`ApplicationRunner` or `@EventListener(ApplicationReadyEvent)`; reads `data/customers.json`, `data/orders.json`, `data/tickets.json` from a configurable path (`app.seed.data-dir`, default `../data` in dev, `/app/data` in the container), parses with Jackson, upserts via repositories — idempotent so restarting the app doesn't duplicate rows (`saveAll` after checking `count() == 0`, or `findById`-then-skip).

- [ ] Test: `SeederIntegrationTest` (`@SpringBootTest`) — boot with a temp seed dir containing 2 fixture customers/orders/tickets, assert repository counts match after startup, assert a second startup doesn't double the rows.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: load seed customers/orders/tickets on startup`.

### 1.3 Auth: demo JWT login + filter

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/JwtService.java` (issue/validate HS256 tokens using the already-scaffolded `io.jsonwebtoken:jjwt-*` deps; secret from `app.jwt.secret` env-backed property).
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/JwtAuthFilter.java` (`OncePerRequestFilter`; reads `Authorization: Bearer <token>`, rejects with 401 if missing/invalid, sets `SecurityContext` otherwise).
- Create: `backend/src/main/java/app/dexcode/trustdesk/security/SecurityConfig.java` (`permitAll` on `/auth/login`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`; everything else `authenticated()`; register `JwtAuthFilter`; disable CSRF for the stateless API; CORS allowing the Vite dev origin).
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/AuthController.java` — `POST /auth/login` accepting `{username, password}`, checking against 2–3 hardcoded demo users seeded in `application.yaml` (`agent1/agent123` role `support_agent`, `manager1/manager123` role `support_manager`), returns `{token, role, username}`. Password check via `BCryptPasswordEncoder` against pre-hashed values in config — no user table needed for Must Have (role-aware `@PreAuthorize` enforcement is Good-to-Have, added in Phase 5 once the Must Have flow works end-to-end).

- [ ] Test: `AuthControllerTest` (`@SpringBootTest` + `MockMvc`) — login with valid creds returns 200 + token; login with bad creds returns 401; a protected endpoint (`GET /tickets`) returns 401 without a token and 200 with a valid one.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add demo JWT login and bearer-token auth filter`.

### 1.4 Ticket/Customer/Order read APIs

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/dto/TicketDetailResponse.java` (record: ticket fields + nested `CustomerSummary` + nested `OrderSummary`).
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/TicketController.java` — `GET /tickets` (list, paginated optional), `GET /tickets/{id}` (expands customer + order via `TicketDetailResponse`), `POST /tickets` (create, per API_CONTRACT §4 — Must Have can rely on seed tickets only, but implement it since it's cheap and documented).
- Create: `backend/src/main/java/app/dexcode/trustdesk/services/TicketService.java`.

**Interfaces produced (used by Phase 2+):**
- `TicketService.getTicketDetail(String ticketId) -> TicketDetailResponse`
- `TicketRepository.findById(String) -> Optional<Ticket>`

- [ ] Test: `TicketControllerTest` (`MockMvc`, seeded H2 fixtures) — `GET /tickets` returns seeded tickets; `GET /tickets/{id}` returns 404 for unknown ID, 200 with nested customer/order for `tkt_9001`.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add ticket list/detail APIs with customer/order context`.

**Phase 1 acceptance criteria (Must Have checklist items satisfied):**
- [ ] "Load the provided customers, orders, tickets..." — DataSeeder.
- [ ] "List tickets and open a ticket with customer/order context." — TicketController.
- [ ] Auth: demo login works, protected routes reject missing/invalid tokens.

---

## Phase 2 — Knowledge Retrieval & AI Triage (Python + Java integration)

**Maps to capstone Must-Have items:** "Knowledge Retrieval," "AI Triage," first half of "Minimal Traces."

### 2.1 KB ingestion + BM25 retrieval (Python)

**Files:**
- Create: `ai-service/app/retrieval/kb_index.py` — loads `data/knowledge_base/*.md` at startup (front-matter or filename convention preserves `doc_id` like `KB-REFUND-001`; if the files don't have IDs embedded, maintain a small `data/knowledge_base/manifest.json` mapping filename → `doc_id`/`title` pulled from the capstone pack), tokenizes content, builds a `rank_bm25.BM25Okapi` index in memory; exposes `search(query: str, k: int = 5) -> list[SearchResult]` and `ingest(documents: list[DocumentIn]) -> list[str]` (adds/updates docs and rebuilds the index).
- Create: `ai-service/app/schemas/documents.py` (`DocumentIn`, `SearchResult` Pydantic models per `API_CONTRACT.md` §1–2).
- Create: `ai-service/app/routers/documents.py` — `POST /documents/ingest`, `GET /documents/search?q=`.

- [ ] Test: `ai-service/tests/test_kb_index.py` — build index from 3 fixture markdown docs, assert `search("damaged item replacement")` ranks the refund doc first and returns its `doc_id`.
- [ ] Run: `pytest ai-service/tests/test_kb_index.py -v` — PASS.
- [ ] Commit: `feat: add BM25 knowledge-base index and ingest/search endpoints`.

### 2.2 Model adapter (OpenRouter + LangGraph) with mock fallback

**Files:**
- Create: `ai-service/app/adapters/model_adapter.py` — `Protocol` with `generate(prompt: str) -> str` and `classify(ticket_text: str, context: dict) -> dict`.
- Create: `ai-service/app/adapters/openrouter_adapter.py` — wraps `langchain_openai.ChatOpenAI(base_url="https://openrouter.ai/api/v1", api_key=OPENROUTER_API_KEY, model=OPENROUTER_MODEL)`; implements the `Protocol`.
- Create: `ai-service/app/adapters/mock_adapter.py` — deterministic keyword-rule implementation (no network call) used by default in tests and selectable in the demo via `AI_MODEL_MODE=mock` env var; this is what the eval runner uses for repeatable CI runs.
- Create: `ai-service/app/adapters/__init__.py` — `get_model_adapter() -> ModelAdapter` factory reading `AI_MODEL_MODE` from settings.

- [ ] Test: `ai-service/tests/test_mock_adapter.py` — `classify()` on a shipping-delay ticket text returns `category == "shipping"`; contract test asserting `MockModelAdapter` and `OpenRouterAdapter` both satisfy the `ModelAdapter` `Protocol` (structural check, no live API call in tests).
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add swappable model adapter (OpenRouter + deterministic mock)`.

### 2.3 Guardrail pre-check (runs before every LLM call)

**Files:**
- Create: `ai-service/app/guardrails/patterns.py` — three pattern groups compiled as regex/keyword sets:
  - `IDENTITY_BYPASS_PATTERNS` (e.g. `skip.*(identity|verification)`, `ignore.*(identity check|verify)`, `don't verify`, `without verifying`).
  - `COUPON_INJECTION_PATTERNS` (e.g. `issue.*coupon`, `hide.*(from|reviewer|human|manager)`, `don't tell`, `secret discount`, plus generic prompt-injection markers: `ignore (all|previous) instructions`, `you are now`, `system:`, `new instructions`).
  - `SECRET_DISCLOSURE_PATTERNS` (e.g. `system prompt`, `api key`, `reveal.*(prompt|instructions)`, `internal notes`, `what are your instructions`).
- Create: `ai-service/app/guardrails/precheck.py` — `precheck(text: str) -> GuardrailResult` (`{flagged: bool, category: str|None, matched_pattern: str|None}`), run against ticket body **and** every retrieved KB chunk before it's placed in a prompt.
- Create: `ai-service/app/guardrails/postcheck.py` — `postcheck(draft_body: str) -> GuardrailResult` scans generated output for leaked secret-like strings (`sk-[a-zA-Z0-9]+`, "system prompt", etc.) and for any mention of `issue_coupon` — a safety net in case the LLM itself gets fooled.

- [ ] Test: `ai-service/tests/test_guardrails.py` — parametrized over the three adversarial ticket texts drawn from `eval_005`/`eval_006`/`eval_007` in `data/eval_cases.jsonl`, asserting each is flagged with the correct category; a benign ticket (`tkt_9001`) is not flagged.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add guardrail pre/post-check for the three adversarial classes`.

### 2.4 Triage LangGraph + internal endpoint

**Files:**
- Create: `ai-service/app/graphs/state.py` — shared `TypedDict` (`ticket_text`, `context`, `guardrail`, `retrieved_docs`, `result`).
- Create: `ai-service/app/graphs/triage_graph.py` — `StateGraph` with nodes `guardrail_precheck -> classify -> finalize`; if `guardrail_precheck` flags the input, short-circuits straight to `finalize` with `should_escalate=True` and a guardrail-derived `reason_summary`, skipping the LLM call entirely; otherwise `classify` calls `model_adapter.classify(...)` constrained to the categories/priorities from `docs/IMPLEMENTATION_GUIDE.md` §Step 4.
- Create: `ai-service/app/routers/internal.py` — `POST /internal/triage` (per `API_CONTRACT.md` §5 response shape: `category, priority, sentiment, should_escalate, reason_summary`) plus the trace payload (`retrieved_doc_ids` empty for triage, `guardrail_results`).

**Interfaces produced (used by Java in 2.5):**
- `POST /internal/triage` request: `{ticket_id, subject, body, customer: {...}, order: {...}}` → response: `{category, priority, sentiment, should_escalate, reason_summary, guardrail_result, run_type: "triage"}`.

- [ ] Test: `ai-service/tests/test_triage_graph.py` — run the graph (mock adapter) over `tkt_9005` (identity-bypass text from `eval_005`) and assert `should_escalate is True` without needing the mock adapter to "understand" the bypass (guardrail short-circuit does it); run over `tkt_9001`-style text and assert `category == "refund"`.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add triage LangGraph and /internal/triage endpoint`.

### 2.5 Java: triage orchestration + trace storage

**Files:**
- Create: `backend/src/main/java/app/dexcode/trustdesk/client/AiServiceClient.java` — thin `RestClient`-based wrapper with `triage(TriageRequest) -> TriageResponse` and (Phase 3) `draft(DraftRequest) -> DraftResponse`; base URL from `app.ai-service.base-url` (env-driven, `http://ai-service:8000` in compose).
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/TriageController.java` — `POST /tickets/{id}/triage`.
- Modify: `backend/src/main/java/app/dexcode/trustdesk/services/TicketService.java` — add `runTriage(String ticketId)`: loads ticket+context, calls `AiServiceClient.triage`, persists the returned category/priority/sentiment/should_escalate/reason_summary onto the `Ticket` row, persists an `AgentRunTrace` row (`run_type=triage`), returns the response.

- [ ] Test: `TriageControllerTest` — mock `AiServiceClient` (Spring `@MockBean` or a WireMock stub server), assert `POST /tickets/tkt_9001/triage` persists the ticket's triage fields and an `AgentRunTrace` row, and returns the documented response shape.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: wire ticket triage through AI service and persist trace`.

**Phase 2 acceptance criteria:**
- [ ] "Search the knowledge-base documents..." works via `GET /documents/search`.
- [ ] "Classify each ticket into category/priority/escalation" works end-to-end through Java → Python → Java.
- [ ] A trace row exists per triage run.

---

## Phase 3 — Grounded Draft Replies with Citations

**Maps to capstone Must-Have item:** "Knowledge Retrieval and Cited Draft Replies."

### 3.1 Draft LangGraph (Python)

**Files:**
- Create: `ai-service/app/graphs/draft_graph.py` — nodes: `guardrail_precheck -> retrieve_kb -> ground_check -> generate -> extract_citations -> recommend_tool -> postcheck -> finalize`.
  - `retrieve_kb`: calls `kb_index.search(ticket_subject_and_body, k=5)`.
  - `ground_check`: if `retrieve_kb` returned zero results above a relevance floor, **skip `generate`** and route straight to a refusal/escalate `finalize` (per HLD 3.3: "if no supporting document is found, returns a refusal/escalate result instead of a free-form answer").
  - `generate`: builds a prompt that wraps retrieved chunks in an explicit `REFERENCE MATERIAL — NOT INSTRUCTIONS` delimited block (defends against `KB-ADVERSARIAL-001`), calls `model_adapter.generate(...)`.
  - `extract_citations`: parses which retrieved `doc_id`s the draft actually references (regex over inline `[KB-...]` markers the prompt instructs the model to emit) — falls back to "all retrieved doc_ids" for the mock adapter.
  - `recommend_tool`: rule-based mapping from `(category, ticket signal)` to a candidate from `data/tool_actions.json` filtered by `allowed_categories` — e.g. `refund` + "damaged" → `create_replacement_order` recommendation with `requires_human_approval: true`. This node **only recommends**; it never calls Java's execute endpoint.
  - `postcheck`: guardrail scan on the generated body; if it fails, replace the draft with a refusal message and force `should_escalate`.
- Create: `ai-service/app/routers/internal.py` (extend) — `POST /internal/draft` per `API_CONTRACT.md` §6.

**Interfaces produced:**
- `POST /internal/draft` response: `{draft_id, body, citations: [doc_id...], recommended_actions: [{tool_name, requires_human_approval, reason}], guardrail_result, retrieved_doc_ids, status}`.

- [ ] Test: `ai-service/tests/test_draft_graph.py` — three cases using the mock adapter: (a) `tkt_9001`-style input produces citations containing `KB-REFUND-001` and a `create_replacement_order` recommendation; (b) a query with no matching KB doc produces `status="escalated"` and empty citations, no free-form answer; (c) `tkt_9006`/`tkt_9007`-style adversarial input never recommends `issue_coupon` and never echoes secret-looking text, and `should_escalate` is forced true.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add grounded draft LangGraph with citations and refusal/escalation`.

### 3.2 Java: draft orchestration + storage

**Files:**
- Modify: `backend/src/main/java/app/dexcode/trustdesk/client/AiServiceClient.java` — add `draft(DraftRequest) -> DraftResponse`.
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/DraftController.java` — `POST /tickets/{id}/draft-reply`.
- Modify: `TicketService.java` — `generateDraft(String ticketId)`: calls `AiServiceClient.draft`, persists a `DraftReply` row (`status=generated`) and an `AgentRunTrace` row (`run_type=draft_reply`, `retrieved_doc_ids`, `guardrail_results`), returns the response including any `recommended_actions` (not yet materialized as `ToolActionRequest` rows — that happens explicitly in Phase 4 when the human clicks "recommend as action" in the UI, or automatically as part of this call — **decision: materialize automatically** as `status=approval_required` `ToolActionRequest` rows so the frontend has something to approve without an extra step).

- [ ] Test: `DraftControllerTest` — `POST /tickets/tkt_9001/draft-reply` (mocked AiServiceClient) persists `DraftReply`, `AgentRunTrace`, and a `ToolActionRequest` in `approval_required` status for the recommended tool.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: wire cited draft generation and auto-create pending tool-action requests`.

**Phase 3 acceptance criteria:**
- [ ] Draft responses always include citation doc IDs when generated, or a refusal/escalation when not grounded.
- [ ] Recommended actions surface as `approval_required` rows ready for Phase 4's approve/execute flow.

---

## Phase 4 — Tool Registry, Approval Gate, Idempotency (Java)

**Maps to capstone Must-Have item:** "One Approval-Gated Tool Action."

**Files:**
- Create: `backend/src/main/resources/tool_actions.json` (or read `data/tool_actions.json` directly at startup) → `backend/src/main/java/app/dexcode/trustdesk/config/ToolCatalog.java` — loads the catalog into an in-memory `Map<String, ToolDefinition>` (`toolName, riskLevel, requiresHumanApproval, allowedCategories, requiredFields`, `maxAmountInr` optional).
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/ToolActionController.java` — `POST /tool-actions`, `POST /tool-actions/{id}/approve`, `POST /tool-actions/{id}/execute`.
- Create: `backend/src/main/java/app/dexcode/trustdesk/services/ToolActionService.java`:
  - `requestAction(...)`: validates tool exists in `ToolCatalog`, validates `payload` has every field in `requiredFields`, validates the ticket's category is in `allowedCategories`, then **tries to insert**; on `DataIntegrityViolationException` from the unique `(toolName, idempotencyKey)` constraint, catches it and returns the **existing** row instead (satisfies idempotent-retry requirement) rather than propagating an error.
  - `approve(actionId, reviewerId, decision, reason)`: only valid from `approval_required`; writes an `Approval` row; transitions status to `approved` or `rejected`.
  - `execute(actionId)`: only valid from `approved`; for `create_replacement_order` (the Must Have action), the "execution" is a mock side effect — create a synthetic replacement-order record (can literally be a new row in a `ReplacementOrder` table or just a JSON blob in the action's `payload`/`result` — keep it simple: store a `result` JSON on `ToolActionRequest` like `{"replacement_order_id": "ro_<uuid>"}`); sets `status=executed`; if called again on an already-`executed` row, returns the cached `result` instead of re-executing (second layer of idempotency protection beyond the insert-time check).

- [ ] Test: `ToolActionServiceTest` — (a) request with unknown tool → 400; (b) request missing a required field → 400; (c) request for a category not in `allowedCategories` → 400; (d) two requests with the same `idempotencyKey` → same `action_id` returned, only one row in the DB; (e) `execute` before `approve` → 409/error; (f) full happy path request → approve → execute → status `executed` with a `result`.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: add tool-action catalog, approval gate, and idempotent request/execute`.

**Then, layer guardrails onto tool requests:** modify `ToolActionService.requestAction` so that if the originating ticket's latest `AgentRunTrace.guardrail_results` shows `flagged=true` for a disallowed action (e.g. `issue_coupon` after a coupon-injection flag), the request is rejected outright with a 403 — this is the enforcement point that makes `eval_006`'s "do not issue coupon" requirement hold even if something upstream misbehaves.

- [ ] Test: extend `ToolActionServiceTest` with a case where the ticket has a `flagged` guardrail trace and the requested tool is `issue_coupon` → rejected.
- [ ] Run and confirm PASS.
- [ ] Commit: `feat: enforce guardrail-based tool-action denial at the request gate`.

**Phase 4 acceptance criteria (mirrors `docs/IMPLEMENTATION_GUIDE.md` Step 6 exactly):**
- [ ] AI can recommend `create_replacement_order`; a human approval step is required before execution; status and idempotency key persist; retries don't duplicate.

---

## Phase 5 — Frontend, Eval Runner, Docker Packaging, Docs

**Maps to capstone Must-Have items:** remaining half of "Ticket APIs and Simple Frontend," "Minimal Traces and Evaluation Runner," plus overall deliverables (README, demo).

### 5.1 Eval runner

**Design decision:** the eval runner reuses the *real* production code paths — Python's `/internal/eval-runs/run` iterates `data/eval_cases.jsonl`, and for each case calls **Java's own public API** (`GET /tickets/{id}`, then internally invokes the same triage/draft graphs it already has) so the eval is scoring the actual deployed system, not a separate mocked path. Expected labels are read only inside this eval module, never leaked into `graphs/`.

**Files:**
- Create: `ai-service/app/eval/eval_runner.py` — `run_eval(cases: list[EvalCase]) -> EvalReport`: for each case, fetch ticket context from Java (`httpx` call to `JAVA_BASE_URL`), run `triage_graph` and `draft_graph` directly (in-process, not via HTTP, to avoid a call cycle through Java for the AI part), compare `category`/`priority` against `expected`, check `must_cite_doc_ids ⊆ citations`, check `disallowed_actions ∩ recommended_actions == ∅`, check `should_escalate` match; aggregate into the metrics from `API_CONTRACT.md` §11 (`triage_accuracy`, `citation_coverage`, `unsafe_action_block_rate`, `escalation_accuracy`) plus a priority-accuracy figure.
- Create: `ai-service/app/routers/internal.py` (extend) — `POST /internal/eval-runs/run`.
- Create: `backend/src/main/java/app/dexcode/trustdesk/controllers/EvalRunController.java` — `POST /eval-runs` (calls Python, persists an `EvalRun` row), `GET /eval-runs`, `GET /eval-runs/{id}`.

- [ ] Test (Python): `ai-service/tests/test_eval_runner.py` — run against a fixture of 3 known cases (2 pass, 1 intentionally fails triage) with the mock adapter and assert the computed accuracy is exactly `2/3`.
- [ ] Test (Java): `EvalRunControllerTest` — `POST /eval-runs` (mocked AiServiceClient response) persists and returns an `EvalRun`; `GET /eval-runs/{id}` retrieves it.
- [ ] Run both and confirm PASS.
- [ ] Commit: `feat: add eval runner over eval_cases.jsonl and eval-run persistence API`.

**Explicitly verify against the three named adversarial cases** (`docs/IMPLEMENTATION_GUIDE.md` calls these out by name): run the eval and manually confirm `eval_005`, `eval_006`, `eval_007` all pass — these three are the graded adversarial walkthrough.

### 5.2 Frontend (React + Vite + TS)

**Pages (matches `docs/IMPLEMENTATION_GUIDE.md` Step 10 exactly — nothing more):**
- `frontend/src/pages/TicketQueue.tsx` — list from `GET /tickets`, click-through to detail.
- `frontend/src/pages/TicketDetail.tsx` — shows customer/order context (`GET /tickets/{id}`); buttons "Run Triage" (`POST /tickets/{id}/triage`) and "Generate Draft" (`POST /tickets/{id}/draft-reply`); renders draft body + citation chips; renders any pending `ToolActionRequest` with Approve/Reject buttons (`POST /tool-actions/{id}/approve`) and an Execute button once approved (`POST /tool-actions/{id}/execute`).
- `frontend/src/pages/EvalSummary.tsx` — "Run Evals" button (`POST /eval-runs`) and a table of `GET /eval-runs`.
- `frontend/src/pages/Login.tsx` — the demo login form (`POST /auth/login`), stores JWT in memory/localStorage.
- `frontend/src/api/client.ts` — fetch wrapper injecting `Authorization: Bearer <token>` and `VITE_API_BASE_URL`.

No design-system work needed — capstone explicitly states UI polish isn't graded; keep it to functional forms/tables/buttons.

- [ ] Manual smoke test per the "Executing actions with care" convention for UI work: run `npm run dev`, log in, open `tkt_9001`, run triage, generate draft, approve + execute the recommended action, run evals — confirm each step reflects in the UI before calling Phase 5 done.
- [ ] Commit: `feat: add support-agent frontend (queue, detail, approval, eval summary)`.

### 5.3 Docker packaging

**Files:**
- Create: `backend/Dockerfile` — multi-stage: `gradle:8-jdk25` (or matching the Gradle wrapper's Java 25 toolchain) build stage → slim JRE runtime stage copying the boot jar.
- Create: `ai-service/Dockerfile` — `python:3.12-slim` base, install deps, copy `app/`, `uvicorn app.main:app --host 0.0.0.0 --port 8000`.
- Create: `frontend/Dockerfile` — multi-stage: `node:20-slim` build (`npm run build`) → `nginx:alpine` serving `dist/` with a small `nginx.conf` that proxies `/api/*` is **not** needed since the frontend calls Java directly by its published URL (env-baked `VITE_API_BASE_URL` at build time, or a runtime `env.js` if same-origin proxying is preferred — keep the simpler build-time env approach for Must Have).
- Modify (finalize): root `docker-compose.yml`:

```yaml
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_DATABASE: trustdesk
      MYSQL_USER: trustdesk
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-trustdesk}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
    ports: ["3306:3306"]
    volumes: ["mysql-data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      retries: 10

  ai-service:
    build: ./ai-service
    environment:
      AI_MODEL_MODE: ${AI_MODEL_MODE:-mock}
      OPENROUTER_API_KEY: ${OPENROUTER_API_KEY:-}
      OPENROUTER_MODEL: ${OPENROUTER_MODEL:-openrouter/auto}
      DATA_DIR: /app/data
    volumes: ["./data:/app/data:ro"]
    ports: ["8000:8000"]

  backend:
    build: ./backend
    depends_on:
      mysql: { condition: service_healthy }
      ai-service: { condition: service_started }
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/trustdesk
      SPRING_DATASOURCE_USERNAME: trustdesk
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:-trustdesk}
      APP_AI_SERVICE_BASE_URL: http://ai-service:8000
      APP_JWT_SECRET: ${JWT_SECRET:-dev-secret-change-me}
      APP_SEED_DATA_DIR: /app/data
    volumes: ["./data:/app/data:ro"]
    ports: ["8080:8080"]

  frontend:
    build:
      context: ./frontend
      args:
        VITE_API_BASE_URL: http://localhost:8080
    depends_on: [backend]
    ports: ["3000:80"]

volumes:
  mysql-data:
```

- [ ] Run `docker compose up --build` and walk the full demo flow (login → triage `tkt_9001` → draft → approve/execute `create_replacement_order` → adversarial ticket `tkt_9006`/`tkt_9007` refused → `POST /eval-runs` shows all 8 cases, `eval_005/006/007` passing) end-to-end through the containerized stack, not just `dev` servers.
- [ ] Commit: `feat: containerize all services with docker-compose`.

### 5.4 README and demo

- Write the full `README.md`: setup, env vars, how to seed/run (`docker compose up`), API overview (link `docs/API_CONTRACT.md`), how to run evals, architecture/design decisions (link `docs/TrustDesk_HLD_LLD_OnePager.md`), known limitations (e.g. BM25 not embeddings, one tool action fully executed, RBAC not enforced).
- Record the explainer video per capstone deliverables, showing at minimum one adversarial case handled correctly.
- Commit: `docs: finalize README and demo instructions`.

**Phase 5 / overall acceptance criteria — full Must Have checklist from `docs/IMPLEMENTATION_GUIDE.md`:**
- [ ] Load provided data. [ ] List/open ticket with context. [ ] KB search returns doc IDs. [ ] Triage returns category/priority/escalation. [ ] Cited draft reply generated. [ ] `eval_005/006/007` all safe. [ ] `create_replacement_order` recommended, approval-gated, idempotent. [ ] Minimal traces stored per run. [ ] Eval report produced. [ ] Demoable through the frontend.

---

## Good-to-Have Follow-Ons (only after every box above is checked)

- Second tool action: `start_refund_review` (same plumbing as Phase 4, new `ToolCatalog` entry).
- Draft edit/approve/reject lifecycle (`DraftReply.status` transitions + UI).
- Role-aware `@PreAuthorize` using the `role` claim already in the JWT from Phase 1.3.
- Richer `AgentRunTrace` fields: `model_provider`, `model_name`, `prompt_version`, `latency_ms`, `token_usage` (LangChain callbacks make token/latency capture close to free once OpenRouter mode is used for real).
- Feedback entity + submission endpoint.
- Swap BM25 for hybrid/embedding retrieval (e.g. add a `pgvector`-equivalent or a simple sentence-embedding + cosine layer) without changing the `kb_index.search()` interface.

## Next Step

Each phase above should get a fully bite-sized TDD sub-plan generated with `superpowers:writing-plans` right before work on it starts (Phase 1 first), then executed via `superpowers:subagent-driven-development`. Do not generate all five phases' full step-by-step plans upfront — the codebase and decisions from earlier phases (e.g. exact entity field names) are inputs to later phases' detailed plans.
