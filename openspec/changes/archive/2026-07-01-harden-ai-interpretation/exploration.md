# Exploration: harden-ai-interpretation

> Read-only exploration for change `harden-ai-interpretation`. Strict TDD mode; `./gradlew test` is the test runner. Artifact store: OpenSpec; persisted to `openspec/changes/harden-ai-interpretation/exploration.md`.

## Current State

The interpretation feature lives entirely in the `assistant` infrastructure edge and one HTTP adapter. There is no AI-specific authorization, no rate limit, and no out-of-scope contract today.

**HTTP surface** (`src/main/java/dio/budgeting/infraestructure/http/TransactionController.java`):
- `POST /transactions/interpret` is a JSON endpoint with `{"prompt":"..."}` body and returns `TransactionDraft(description, amount, category)` directly.
- Authentication is enforced globally by `SecurityConfig` (`anyRequest().authenticated()`). The handler does not call `AuthenticatedUserProvider`; the draft carries no owner id.
- The controller validates the prompt via `AssistantInputValidator.validatePrompt` (non-blank, ≤ 4 000 chars) and then delegates to `transactionAssistant.interpret(prompt)`.

**AI orchestration** (`src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java`):
- Builds a separate `ChatClient` (`interpretationChatClient`) whose only system prompt is the hardcoded Spanish string `INTERPRETATION_SYSTEM_PROMPT`. The prompt tells the model to extract `description`, `amount` (integer centavos), and `category` from the 19-value `Category` enum, using `null` for uncertain fields, and to never persist.
- Calls `interpretationChatClient.prompt().user(prompt).call().entity(TransactionDraft.class)` with no timeout, no retry, no `tools`/output guardrails.
- Logs only `transaction_ai_interpret_started promptLength={}`. No log of the prompt text, no correlation id, no outcome log, and no model-provider latency.
- Wraps any `RuntimeException` from the call as `AssistantIntegrationException`.

**Contracts and error mapping**:
- `TransactionDraft` is a public record `(String description, Long amount, Category category)`. A null `amount` and/or null `category` is a successful 200 with a partial draft.
- `AssistantExceptionHandler` maps `AssistantValidationException → 400 assistant_validation_error` and `AssistantIntegrationException → 502 assistant_integration_error`. There is no semantic-rejection status (e.g. 422) and no rate-limit status (e.g. 429).

**Auth model**:
- `UserRole` is a single-value enum (`USER`). `UserDetailsService` grants `ROLE_USER` to every account. There is no AI-specific role/authority.
- `SecurityConfig` uses one `anyRequest().authenticated()` matcher; no per-endpoint authorization is possible with the current role.

**Existing tests** (`src/test/java/...`):
- `TransactionAssistantFacadeTest`: happy path interpretation, blank-prompt rejection, system-prompt literal pinning, prompt-client separation, tool registration, audio path coverage, integration-error wrapping.
- `TransactionControllerTest`: 200 happy path with `$.description/amount/category` assertions, blank prompt → 400 `assistant_validation_error`, integration error → 502 `assistant_integration_error`, JSON-contract stability, no-persistence via `verifyNoInteractions(transactionService)`.
- `SecurityEndpointIntegrationTest`: anonymous → 401, authenticated → 200, prompt router, ownership scoping for transactions.
- There is **no** test for: out-of-scope prompt rejection, prompt-injection guard, rate limit/quota, model returning pesos / unparseable / non-Category values, timeouts, log redaction, or AI-specific authorization.

## Affected Areas

| Area | Impact | Why it matters for hardening |
|------|--------|------------------------------|
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modified | Interpret handler must call new input/content checks, structured result handling, and any rate-limit/rejection paths. |
| `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java` | Modified | System prompt needs an explicit out-of-scope / injection-guard clause; the call site should be wrapped in timeout + retry, with structured logging (length, hash, latency, outcome, rejection reason). |
| `src/main/java/dio/budgeting/infraestructure/ai/AssistantInputValidator.java` | Modified | Needs new pre-call checks (already enforces 4 000 chars; should also enforce minimum length and basic content sanity for the AI surface). |
| `src/main/java/dio/budgeting/infraestructure/http/assistant/AssistantExceptionHandler.java` | Modified | Needs at least one new error code (e.g. `assistant_out_of_scope` → 422, `assistant_rate_limited` → 429) and possibly a guarded redaction of the underlying message for 502s. |
| `src/main/java/dio/budgeting/infraestructure/ai/TransactionDraft.java` | Modified (additive) | May gain a `status` discriminator (`OK`, `INCOMPLETE`, `OUT_OF_SCOPE`) or be wrapped in a new `InterpretationResult` envelope so callers can distinguish "model returned nulls because uncertain" from "model rejected the prompt". Wire shape must stay backward compatible. |
| `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistant.java` | Modified (signature) | May expose a richer return type. Interface widening is contained to the assistant package. |
| `src/main/java/dio/budgeting/infraestructure/ai/AssistantIntegrationException.java` / new `AssistantRateLimitedException` / new `AssistantSemanticRejectionException` | New | New domain exceptions kept under `assistant/`. |
| `src/main/java/dio/budgeting/infraestructure/http/assistant/AssistantErrorResponse.java` | Modified (additive) | New error codes only; no field renames. |
| `src/main/java/dio/budgeting/config/SecurityConfig.java` | Possibly modified | If AI-specific authorization is added, add a request matcher for `/transactions/interpret` with the new role/authority. |
| `src/main/java/dio/budgeting/domain/user/UserRole.java` | Unchanged (intentional) | Adding a new role value would expand product scope. Kept `USER` only; AI hardening stays available to every authenticated user. |
| `src/main/resources/application.properties` | Possibly modified | New timeout/retry knobs for the OpenAI chat client (`spring.ai.openai.chat.options.timeout`, retry, max-tokens). |
| `src/test/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacadeTest.java` | Modified | Update `EXPECTED_INTERPRETATION_PROMPT` literal; add rejection / log / timeout tests. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Modified | Add 422 / 429 / out-of-scope / prompt-injection / new envelope-shape coverage. |
| `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java` | Modified | Add a focused auth-vs-anon test for the new behavior (still 401, plus a happy-path authenticated interpretation that exercises the hardened response shape). |
| `openspec/specs/transaction-api/spec.md` | Modified (additive) | New scenarios: "Reject out-of-scope prompts" (422), "Enforce per-session rate limit" (429), "Do not log or echo the prompt text" (INFO). |

## Approaches

1. **Minimal-surface hardening (recommended)** — keep the existing `TransactionDraft` contract; introduce a thin result envelope (or status field) that lets the model signal `OUT_OF_SCOPE` distinctly from "incomplete but valid". Add a `MAX_PROMPT_LENGTH` plus a minimum-length check, basic prompt-injection guard, an explicit out-of-scope clause in the system prompt, a small in-memory per-session rate limit, structured `WARN`/`INFO` logs that include length and SHA-256 (no raw prompt), and a new `assistant_out_of_scope → 422` + `assistant_rate_limited → 429` error mapping. Tests-first per strict TDD. **No role change, no DB change, no `/transactions/interpret` path or wire-field rename.**
   - Pros: addresses the highest-priority security/abuse concerns (prompt injection, cost overrun, semantic rejection, prompt leakage) without expanding product scope or breaking the wire contract. Smallest reviewable diff.
   - Cons: rate limit lives in-process; horizontal scaling loses the bucket. Solved for MVP by documenting this as a known follow-up (e.g. `redis-bucket` later).
   - Effort: Medium

2. **Defense-in-depth hardening** — everything in (1) plus: new `UserRole` value (e.g. `AI_USER`) with a dedicated authority, per-role quota (e.g. 30/min for `USER`, 120/min for `AI_USER`), HTTP request matcher in `SecurityConfig` mapping `/transactions/interpret` to `hasAuthority('SCOPE_AI_INTERPRET')`, response-envelope migration across all assistant endpoints, prompt-redaction middleware in front of `ChatClient`, and explicit retry/back-off.
   - Pros: clearest authorization story, easier audit, future-proof for premium/paid tiers.
   - Cons: expands product scope (new role, new SCOPE authority, cross-endpoint migration). Larger diff, more tests, more docs. Out of scope per the parent's "harden without expanding product scope" instruction.
   - Effort: High

3. **Status-quo + spec hygiene only** — keep the current code; just add spec scenarios documenting today's behavior and add tests that pin the existing 200/400/502 contract.
   - Pros: minimal effort; zero risk of regression.
   - Cons: leaves prompt injection, cost overrun, log leakage, and out-of-scope abuse unaddressed. Fails the "harden" intent.
   - Effort: Low

## Recommendation

Adopt **Approach 1 (minimal-surface hardening)**.

Concretely:

- Tighten `AssistantInputValidator` to also reject empty-after-trim prompts under a small minimum (e.g. 3 chars) and prompts that match a small set of obvious injection markers (system/ignore markers, multi-line control sequences) before any AI call is made.
- Extend `INTERPRETATION_SYSTEM_PROMPT` with an explicit out-of-scope contract: "If the prompt is not about a personal expense, return an empty description, null amount, null category, and a top-level `status: OUT_OF_SCOPE`." Spring AI's `entity(...)` returns the full object so the envelope status must be a new top-level field on the response.
- Change `TransactionAssistant.interpret` to return an `InterpretationResult` envelope (status + draft) and update the controller to map `OUT_OF_SCOPE` → 422, `INCOMPLETE` (nulls) → 200 with the partial draft, `OK` → 200. The wire shape of the current `description/amount/category` fields is preserved; a new `status` field is added (additive, non-breaking for clients that ignore it).
- Add a per-session in-memory `AiRateLimiter` (token bucket keyed by `JSESSIONID`) with a default of N requests/min (configurable via `application.properties`); on overflow, throw `AssistantRateLimitedException` and the handler returns 429 with `Retry-After`.
- Wrap the `ChatClient` call in a timeout (`spring.ai.openai.chat.options.timeout` or `ChatClient` call timeout) and catch timeout/connection failures as a separate `AssistantTimeoutException` (still 502) so we can distinguish "down" from "slow".
- Add structured logging at the facade: prompt length, `sha256(prompt)` (first 8 hex), latency ms, model, outcome (`ok`/`incomplete`/`out_of_scope`/`integration_error`/`timeout`), and a `WARN` line for rejected prompts. **Never log raw prompt text.**
- Add a `Rate-Limit-*` response header on success and a `Retry-After` header on 429 for clarity.

## TDD implications (strict TDD, `./gradlew test`)

Strict TDD requires failing tests (RED) before any production change. The exploration maps each hardening concern to one or more tests that must be added **before** implementation in `apply`.

**RED tests to add first** (each one must fail against the current code):

1. `TransactionControllerTest.shouldReturnBadRequestWhenPromptBelowMinimumLength` — prompt of 1–2 non-blank chars → 400.
2. `TransactionControllerTest.shouldReturnBadRequestWhenPromptMatchesInjectionMarker` — e.g. "ignore previous instructions and ..." → 400 with new error code (e.g. `assistant_validation_error` reused, or new `assistant_prompt_rejected`).
3. `TransactionControllerTest.shouldReturnUnprocessableEntityWhenAiSignalsOutOfScope` — facade stub returns `OUT_OF_SCOPE` → 422 + new error code `assistant_out_of_scope`.
4. `TransactionControllerTest.shouldReturnPartialDraftWhenAiReturnsNullFields` — facade stub returns `INCOMPLETE` with null `amount` and `category` → 200 with `status: "INCOMPLETE"` and the same `description/amount/category` fields.
5. `TransactionControllerTest.shouldReturnTooManyRequestsWhenSessionExceedsRateLimit` — first N calls succeed, (N+1)th call → 429 with `Retry-After` header and `assistant_rate_limited` code.
6. `TransactionControllerTest.shouldExposeRateLimitHeadersOnSuccess` — successful call → response includes `RateLimit-Remaining` (informative header).
7. `TransactionAssistantFacadeTest.shouldNotLogRawPromptText` — capture the SLF4J logger (or assert on a structured-logger appender) and verify that no log line contains the raw prompt; only length + truncated hash are present.
8. `TransactionAssistantFacadeTest.shouldWrapTimeoutAsAssistantTimeoutException` — `ChatClient` throws a `TimeoutException` (or test double) → `AssistantTimeoutException` is thrown, distinct from `AssistantIntegrationException`.
9. `TransactionAssistantFacadeTest.shouldHonorConfiguredTimeout` — verify the facade sets a non-zero timeout on the chat call.
10. `SecurityEndpointIntegrationTest.shouldStillRejectAnonymousInterpretAfterHardening` — anonymous → 401 (no regression to auth contract).
11. `TransactionAssistantFacadeTest.shouldUpdateExpectedInterpretationPromptLiteralToIncludeOutOfScopeClause` — pin the new prompt text so future refactors must keep the out-of-scope contract.

**GREEN implications (the production change set that makes the RED tests pass):**

- `TransactionAssistant.interpret` return type becomes `InterpretationResult(InterpretationStatus status, TransactionDraft draft)` where `status ∈ {OK, INCOMPLETE, OUT_OF_SCOPE}`. `TransactionDraft` stays a public record; the envelope is new but additive on the wire.
- `TransactionAssistantFacade` gets a new `interpretationChatClient` configuration with `defaultOptions(timeout=...)`, builds the envelope from the model's return (null fields ⇒ `INCOMPLETE`, explicit out-of-scope signal ⇒ `OUT_OF_SCOPE`), uses a single `Logger` with parameterized calls, and a new `AssistantTimeoutException` is introduced.
- `AssistantInputValidator` adds `MIN_PROMPT_LENGTH` and a small `INJECTION_MARKERS` set; the validation result is still `AssistantValidationException`.
- A new `AiRateLimiter` component (singleton, in-memory `ConcurrentHashMap<sessionId, Bucket>`) is consulted by the controller before delegating; controller reads `HttpSession` id.
- `AssistantExceptionHandler` adds handlers for `AssistantSemanticRejectionException → 422` and `AssistantRateLimitedException → 429` with `Retry-After`.
- `application.properties` gains `ai.interpret.rate-limit.requests-per-minute` and `spring.ai.openai.chat.options.timeout` keys; both are read via `@ConfigurationProperties` (no hardcoded constants in code).

**Non-RED test updates (assertions that must be updated in lockstep):**

- `TransactionAssistantFacadeTest.EXPECTED_INTERPRETATION_PROMPT` literal is updated to include the out-of-scope clause; otherwise `verify(chatClientBuilder).defaultSystem(...)` will fail.
- Existing `TransactionControllerTest` happy-path tests must be updated to mock the new `InterpretationResult` envelope (or use a default factory that wraps the existing draft as `OK`).
- `SecurityEndpointIntegrationTest.shouldPreserveAuthenticatedAssistantAndAiEndpointContractsThroughSecurity` must mock the new envelope shape.

## Security review (concise)

| # | Severity | Category | Where | Risk | Recommended control |
|---|----------|----------|-------|------|---------------------|
| 1 | **HIGH** | Input handling | `TransactionAssistantFacade.interpret` | User-controlled prompt is sent verbatim to the OpenAI chat model. Adversarial prompts can extract the system prompt, trick the model into emitting non-`Category` values, or shift the response shape. | Add an explicit out-of-scope clause to the system prompt; treat `OUT_OF_SCOPE` as a structured 422; reject obvious injection markers pre-call; do not trust `entity(TransactionDraft.class)` blindly — validate `category` against the enum and reject or coerce. |
| 2 | **HIGH** | Availability | `POST /transactions/interpret` (no quota) | A logged-in user can fire thousands of calls and exhaust OpenAI quota, costing real money and DoS-ing other users. | Per-session in-memory token bucket with a configurable cap; return 429 with `Retry-After`. Document the in-process caveat. |
| 3 | **MEDIUM** | Exposure | `TransactionAssistantFacade.interpret` log line | Current log only records length, so PII in the prompt is not leaked today — but the new "I want to log the rejection reason" temptation will leak it. | Logging policy: length + truncated SHA-256 + outcome tag only. Add a test that asserts raw prompt text never appears in any log event. |
| 4 | **MEDIUM** | Input handling | `AssistantInputValidator.validatePrompt` | Only enforces non-blank and ≤ 4 000 chars. A single-character prompt reaches the model, a 4 000-char prompt can include large pasted blobs. | Add a sensible minimum (e.g. 3) and a small list of injection markers. Keep the upper cap. |
| 5 | **MEDIUM** | Business logic | `TransactionAssistantFacade.interpret` | On any model-side RuntimeException the user gets a 502 with the underlying message in the body. Provider error text can leak internals (rate-limit counters, model names, stack-trace hints). | Catch only `TimeoutException` and a known set of Spring AI exceptions as 502 with a sanitized message; everything else re-throws. Add a `WARN` log with the original cause for operators. |
| 6 | **LOW** | AuthZ | `SecurityConfig` (global `anyRequest().authenticated()`) | All authenticated users can call `/transactions/interpret`. There is no separate authority. | Defer role-based authorization (Approach 2) — out of scope. Document this as a known limitation. |
| 7 | **LOW** | Availability | `ChatClient` call | No timeout; a stalled model request can hold a request thread indefinitely. | Configure `spring.ai.openai.chat.options.timeout` (or a `ChatClient`-level timeout) and map timeout to a distinct `AssistantTimeoutException` so callers see a clean 502 with `assistant_timeout`. |
| 8 | **INFO** | Output handling | `TransactionDraft` JSON serialization | The current response allows `null` `amount` and `null` `category` on a 200. Clients that don't check for `null` could break. | Document the new `status` field clearly; keep the existing nullable fields but make `status: "INCOMPLETE"` the explicit signal for "model returned nulls". |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Wire-shape regression: existing clients break if `TransactionDraft` is replaced by an envelope. | Med | Add the envelope as a new wrapper; keep `description/amount/category` at the top level of the JSON. Update tests in lockstep. |
| Rate-limit bucket lost on multi-instance deployment. | Med | Document the in-process limitation; design the `AiRateLimiter` interface so a `redis-bucket` backend is a drop-in replacement later. |
| Spring AI `entity(TransactionDraft.class)` may fail to parse the new envelope on the way back, or the model may not honor the `status` field. | Med | Treat the model's compliance as best-effort; the Java side coerces "all fields null" → `INCOMPLETE`, "non-null but unknown category" → coerce to `OTROS` or reject, and reserves `OUT_OF_SCOPE` for an explicit model signal. Add tests for each. |
| Log redaction test is brittle (depends on logger capture mechanism). | Med | Use a deterministic log appender (e.g. Logback `ListAppender`) wired only in the test, not in production code. |
| Tightening the system prompt changes model behavior on existing happy paths. | Med | Update `EXPECTED_INTERPRETATION_PROMPT` literal and keep one `TransactionControllerTest` happy path unchanged (still returns `Coffee and bread / 2300L / COMIDA`) to lock the contract. |
| New error codes are not yet covered by the OpenSpec spec, leaving the API contract ambiguous. | Low | Update `openspec/specs/transaction-api/spec.md` with new scenarios in the same change. |
| Auth-vs-authz distinction (user *is* authenticated, but should not be allowed to call the AI endpoint) is unresolved. | Low (deferred) | Document as known limitation; do not introduce a new role in this change. |

## Ready for Proposal

Yes — the next step is a proposal that:

- Adds the `InterpretationResult` envelope with a new `status` discriminator, keeping the existing `description/amount/category` wire fields and adding `status` additively.
- Adds `assistant_out_of_scope → 422` and `assistant_rate_limited → 429` error mappings, plus the matching exceptions.
- Tightens `AssistantInputValidator` with a minimum prompt length and a small set of injection markers.
- Adds a per-session in-memory `AiRateLimiter` (configurable via `application.properties`) and `RateLimit-*` / `Retry-After` response headers.
- Adds an explicit out-of-scope clause to `INTERPRETATION_SYSTEM_PROMPT` and a timeout on the chat call, with `AssistantTimeoutException` mapped to 502.
- Updates structured logs to record length, truncated SHA-256, latency, and outcome — never raw prompt text.
- Updates `openspec/specs/transaction-api/spec.md` with new Given/When/Then scenarios for the hardened behavior, while preserving the existing 200/400/502/401 contract for clients that ignore the new fields.

Explicitly **out of scope** for this change (deferred, not denied): new `UserRole` values, per-role quotas, response-envelope migration across the demo `/api/*` assistant endpoints, and a `redis-bucket` rate-limit backend. Adding any of these would expand product scope and is a follow-up change.
