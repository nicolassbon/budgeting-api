# Design: Harden `/transactions/interpret`

This change hardens only `POST /transactions/interpret` while preserving the current layered boundaries and the existing successful payload fields (`description`, `amount`, `category`). The implementation adds an additive success discriminator (`status`), semantic rejection (`422 assistant_out_of_scope`), per-session in-memory rate limiting (`429 assistant_rate_limited`), AI timeout handling (`502 assistant_timeout`), stronger prompt validation, and telemetry-safe logging with no raw prompt text.

## Quick path

1. Add RED tests for validation, `OUT_OF_SCOPE`, `INCOMPLETE`, rate limit, timeout, headers, and log redaction.
2. Introduce an internal interpretation result contract plus controller/exception mapping without changing the endpoint path.
3. Add config-backed rate limiting and timeout handling, then run `./gradlew test`.

## Scope and boundaries

- In scope: `/transactions/interpret` only.
- Out of scope: `/transactions/ai`, `/api/*` assistant demos, new roles, distributed quotas, DB schema work.
- Migration impact: **none for DB schema**.
- Architecture rule: keep business/domain layers unchanged; hardening remains in assistant + HTTP infrastructure around the existing transaction flow.

## Design summary

| Topic | Decision |
|---|---|
| Success payload | Keep top-level `description`, `amount`, `category`; add top-level `status` (`OK` or `INCOMPLETE`). |
| Out-of-scope | Map explicit assistant semantic rejection to `422 assistant_out_of_scope`; do not return a draft body in that case. |
| Validation | Keep blank/max-length checks and add minimum trimmed length plus a small fixed set of obvious injection markers before any AI call. |
| Rate limit | Add an in-memory per-session limiter behind a small interface, keyed by authenticated HTTP session id. |
| Timeout | Apply a non-zero interpretation timeout and map it to `AssistantTimeoutException` → `502 assistant_timeout`. |
| Logging | Log prompt length, truncated SHA-256, latency, and outcome only; never log prompt text. |
| Layering | Controller handles HTTP/session/headers, assistant facade handles AI orchestration, validator handles input rules, exception handler maps HTTP errors. |

## Current architecture fit

The feature already sits in infrastructure:

- `TransactionController` exposes `/transactions/interpret`.
- `TransactionAssistantFacade` owns Spring AI orchestration.
- `AssistantInputValidator` applies pre-AI checks.
- `AssistantExceptionHandler` maps assistant exceptions to HTTP responses.

The hardening stays in the same area. No new domain concepts or persistence changes are required.

## Proposed runtime flow

### Successful request

1. Authenticated client calls `POST /transactions/interpret`.
2. `TransactionController` validates prompt via `AssistantInputValidator`.
3. Controller checks the session-scoped `AiInterpretRateLimiter`.
4. Controller delegates to `TransactionAssistant.interpret(prompt)`.
5. `TransactionAssistantFacade` logs a start-safe telemetry event, calls the interpretation `ChatClient` with hardened system prompt and timeout, then normalizes the result.
6. Controller maps:
   - `OK` → `200` with `description`, `amount`, `category`, `status: "OK"`
   - `INCOMPLETE` → `200` with same fields and `status: "INCOMPLETE"`
7. Controller adds advisory `RateLimit-*` headers.

### Rejected/failed request

- Validation failure before AI call → `400 assistant_validation_error`
- Session over limit before AI call → `429 assistant_rate_limited` + `Retry-After`
- Assistant says prompt is not a personal expense → `422 assistant_out_of_scope`
- AI call times out → `502 assistant_timeout`
- Other provider/transport failure → `502 assistant_integration_error`

## Internal contract changes

### New internal result type

Introduce an internal result envelope returned by `TransactionAssistant`:

- `InterpretationResult`
  - `InterpretationStatus status`
  - `String description`
  - `Long amount`
  - `Category category`

`InterpretationStatus` values:

- `OK`
- `INCOMPLETE`
- `OUT_OF_SCOPE`

Rationale:
- keeps the wire contract backward-compatible
- prevents overloading `null` fields to mean both partial and rejected
- keeps semantic interpretation concerns inside the assistant boundary

### HTTP response shape

Use a dedicated HTTP response DTO for `/transactions/interpret`, not `TransactionDraft` directly.

Successful response shape:

```json
{
  "description": "Coffee and bread",
  "amount": 2300,
  "category": "COMIDA",
  "status": "OK"
}
```

Partial response shape:

```json
{
  "description": "Coffee",
  "amount": null,
  "category": null,
  "status": "INCOMPLETE"
}
```

Out-of-scope remains an error payload via `AssistantErrorResponse`.

## Input hardening design

`AssistantInputValidator.validatePrompt` remains the single pre-AI validator for this surface, expanded with:

- existing: non-blank
- existing: max 4000 chars
- new: minimum trimmed content length (proposal target: 3)
- new: reject a small reviewable marker list such as:
  - `ignore previous instructions`
  - `system prompt`
  - similar explicit override phrases only

Design constraints:
- keep the list intentionally small to limit false positives
- perform case-insensitive matching on normalized text
- reuse `AssistantValidationException` so the existing `400 assistant_validation_error` contract stays stable

## Prompt and AI orchestration design

`TransactionAssistantFacade` will harden `INTERPRETATION_SYSTEM_PROMPT` to explicitly require:

- personal-expense interpretation only
- ignore instruction-override attempts
- never persist anything
- return the expected fields and status semantics
- classify non-expense prompts as `OUT_OF_SCOPE`

The facade remains responsible for:

- calling the interpretation-specific `ChatClient`
- applying timeout protection
- translating provider exceptions into assistant exceptions
- normalizing model output into `InterpretationResult`

Normalization rules:

- explicit assistant rejection → `OUT_OF_SCOPE`
- valid description + amount + category → `OK`
- expense-like result with missing nullable fields → `INCOMPLETE`
- unknown/invalid category from model should not leak through unchecked; coerce through enum parsing and fail safely if invalid

## Rate-limiting design

### Component

Add a small interface, for example:

- `AiInterpretRateLimiter`
  - `RateLimitDecision check(String sessionId)`

And an in-memory implementation using a `ConcurrentHashMap` bucket per session.

### Why session-scoped

- matches current auth/session model (`JSESSIONID`)
- minimal change in HTTP layer
- satisfies the proposal without adding roles, durable quotas, or cross-node infrastructure

### Behavior

- Only authenticated requests reach the controller, so anonymous `401` remains enforced by Spring Security before limiter use.
- The controller reads the current session id and consults the limiter before the AI call.
- On overflow, throw `AssistantRateLimitedException` containing retry metadata.
- On success, expose advisory headers such as:
  - `RateLimit-Limit`
  - `RateLimit-Remaining`
  - optionally `RateLimit-Reset`
- On rejection, return `Retry-After`.

### Tradeoff

This limiter is intentionally process-local. It does not coordinate across instances. The interface boundary keeps later Redis/distributed replacement cheap.

## Timeout and error design

Add typed configuration for interpretation hardening, including:

- requests per minute
- timeout duration
- minimum prompt length
- injection markers list (optional, if kept configurable)

Recommended binding style: `@ConfigurationProperties` under a focused prefix such as `ai.interpret.*`.

Timeout handling in the facade:

- detect timeout exceptions separately from generic runtime/provider failures
- wrap timeout as `AssistantTimeoutException`
- wrap non-timeout provider/transport failures as `AssistantIntegrationException`
- client-facing messages stay sanitized; only server logs keep exception detail

## Logging and privacy design

Add structured logs around interpretation attempts with fields like:

- `promptLength`
- `promptHash` = first 8 hex chars of SHA-256
- `latencyMs`
- `outcome`
- `model` when available

Allowed outcomes:

- `ok`
- `incomplete`
- `out_of_scope`
- `validation_error`
- `rate_limited`
- `timeout`
- `integration_error`

Rules:
- never log raw prompt text
- never log full hash
- log exception detail only on server side for operational debugging

## Exception mapping

Extend `AssistantExceptionHandler` with new mappings:

| Exception | HTTP | Error code |
|---|---:|---|
| `AssistantValidationException` | 400 | `assistant_validation_error` |
| `AssistantSemanticRejectionException` | 422 | `assistant_out_of_scope` |
| `AssistantRateLimitedException` | 429 | `assistant_rate_limited` |
| `AssistantTimeoutException` | 502 | `assistant_timeout` |
| `AssistantIntegrationException` | 502 | `assistant_integration_error` |

`AssistantErrorResponse` can remain unchanged structurally.

## File change plan

| File | Change |
|---|---|
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Inject limiter, use new interpretation response DTO/result mapping, add rate-limit headers. |
| `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistant.java` | Change `interpret` return type from `TransactionDraft` to internal `InterpretationResult`. |
| `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java` | Harden prompt, add timeout handling, normalization, and safe telemetry logging. |
| `src/main/java/dio/budgeting/infraestructure/ai/AssistantInputValidator.java` | Add min-length and injection-marker checks. |
| `src/main/java/dio/budgeting/infraestructure/http/assistant/AssistantExceptionHandler.java` | Add 422/429/timeout mappings and headers. |
| `src/main/java/dio/budgeting/infraestructure/ai/*` | Add new exceptions and interpretation result/status types. |
| `src/main/java/dio/budgeting/.../response/*` | Add dedicated interpret response DTO if needed to keep HTTP contract explicit. |
| `src/main/resources/application.properties` | Add `ai.interpret.*` knobs. |
| `src/test/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacadeTest.java` | Add prompt literal, timeout, normalization, and log-redaction tests. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Add 400/422/429/headers/status behavior tests. |
| `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java` | Re-pin anonymous 401 and authenticated hardened response shape. |

## TDD plan

Implement in strict RED → GREEN order.

### RED tests first

1. Reject prompt below minimum length with `400 assistant_validation_error`.
2. Reject prompt containing an obvious injection marker with `400 assistant_validation_error` and no assistant invocation.
3. Return `422 assistant_out_of_scope` when the assistant returns `OUT_OF_SCOPE`.
4. Return `200` with `status: "INCOMPLETE"` for partial drafts.
5. Return `429 assistant_rate_limited` with `Retry-After` after per-session limit exhaustion.
6. Expose `RateLimit-*` headers on successful responses.
7. Verify logs do not contain the raw prompt text.
8. Wrap timeout as `AssistantTimeoutException` / `assistant_timeout`.
9. Pin the updated interpretation system prompt.
10. Preserve anonymous `401 Unauthorized` for `/transactions/interpret`.
11. Preserve happy-path amount-in-centavos and top-level field compatibility.

### GREEN implementation order

1. Add result/status types and update mocks/tests.
2. Add HTTP response DTO and controller mapping.
3. Add validation hardening.
4. Add new exceptions and exception-handler mappings.
5. Add in-memory limiter and response headers.
6. Add timeout config and facade wrapping.
7. Add safe telemetry logging.

## Tradeoffs

| Choice | Benefit | Cost |
|---|---|---|
| Internal result envelope + top-level HTTP status field | Preserves client compatibility while making semantics explicit | Requires interface and test updates |
| Session-scoped in-memory limiter | Smallest change, fits current auth model | Not multi-node accurate |
| Small fixed injection-marker set | Reviewable and predictable | May miss sophisticated prompt attacks |
| Sanitized client errors | Avoids provider detail leakage | Less direct client diagnostics |
| Infrastructure-only hardening | Preserves layered boundaries and review size | Defers richer policy/entitlement controls |

## Migration and rollout

### Migration impact

- Database schema: **none**
- Existing endpoint path: unchanged
- Existing success fields: unchanged, additive `status` only
- Existing error payload shape: unchanged, additive error codes only

### Config rollout

Add defaults in `application.properties` for safe local behavior. Production can tune:

- `ai.interpret.rate-limit.requests-per-minute`
- `ai.interpret.timeout`
- `ai.interpret.min-prompt-length`

### Operational rollout

1. Merge tests and implementation together.
2. Validate `./gradlew test` passes.
3. Deploy with conservative defaults.
4. Observe logs/429 rate for false positives or overly strict limits.
5. Tune config without code changes if needed.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Clients assume only 3 fields on success | Keep fields top-level and additive; document `status` as optional to consume. |
| False positives from injection markers | Keep the list short and focused on explicit override phrases. |
| Model ignores new prompt contract | Normalize result server-side; reserve `OUT_OF_SCOPE` for explicit rejection semantics. |
| Multi-instance deployments bypass local quota accuracy | Document limitation and keep limiter behind interface for future distributed backend. |
| Timeout wrapping varies by Spring AI exception type | Centralize timeout detection in facade tests and keep fallback generic 502 mapping. |

## Review checklist

- [ ] `/transactions/interpret` remains authenticated and path-compatible
- [ ] `description`, `amount`, and `category` stay top-level on `200`
- [ ] `status` is additive only
- [ ] `OUT_OF_SCOPE` maps to `422 assistant_out_of_scope`
- [ ] rate-limited requests do not call the AI client
- [ ] timeout and generic provider failures are distinguishable at the error-code level
- [ ] no raw prompt text appears in logs
- [ ] no DB schema or role changes are introduced
