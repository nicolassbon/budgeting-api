# Proposal: Harden `/transactions/interpret`

Harden the existing AI expense interpretation endpoint without broadening it into a general chatbot or introducing new product roles. The change keeps `/transactions/interpret` authenticated and compatible for existing clients that consume `description`, `amount`, and `category`, while adding explicit rejection, rate-limit, timeout, logging, and test coverage around the current AI flow.

## Intent

`POST /transactions/interpret` currently sends an authenticated user's prompt directly to the AI interpretation client after only non-blank and max-length validation. It returns a `TransactionDraft` directly, has no semantic out-of-scope signal, no AI-specific quota, no timeout contract, and no tests for prompt injection, out-of-scope prompts, rate limiting, or logging hygiene.

This change will harden that single endpoint so the system can safely interpret personal expense prompts while rejecting unrelated or adversarial input, limiting abuse/cost, preserving privacy in logs, and making partial AI results explicit.

## Scope

### In scope

- Harden `POST /transactions/interpret` only.
- Preserve the existing endpoint path and existing top-level response fields:
  - `description`
  - `amount` in centavos
  - `category`
- Add an additive top-level response signal, expected as `status`, so clients can distinguish:
  - `OK`
  - `INCOMPLETE`
- Map out-of-scope interpretation to a structured error response instead of a successful draft:
  - `422 Unprocessable Entity`
  - error code such as `assistant_out_of_scope`
- Add prompt/input hardening before any AI call:
  - keep the current max length
  - add a small minimum length
  - reject obvious instruction-override/prompt-injection markers
- Strengthen the interpretation system prompt with an explicit personal-expense-only and out-of-scope contract.
- Add per-session in-memory rate limiting for `/transactions/interpret`:
  - `429 Too Many Requests` on overflow
  - `Retry-After` on 429
  - informational `RateLimit-*` headers on successful calls where practical
- Add timeout handling for the AI call:
  - sanitize provider-facing errors
  - map timeout to a distinct assistant error code while preserving a 502-class integration failure contract
- Add structured logging hygiene:
  - log prompt length, truncated SHA-256 hash, latency, model/outcome where available
  - never log raw prompt text
- Add/update deterministic tests first under strict TDD mode using `./gradlew test`.
- Update `openspec/specs/transaction-api/spec.md` with additive hardened behavior scenarios.

### Out of scope

- New AI product surfaces or a general chatbot.
- Renaming or moving `/transactions/interpret`.
- Broad hardening or response-shape migration for root `/api/*` assistant demo endpoints.
- Role-based AI authorization or new `UserRole` values.
- Per-role quotas or paid-tier entitlement behavior.
- Multi-node/distributed rate limiting, Redis buckets, or durable quota storage.
- Database schema changes.
- Expanding `/transactions/ai` audio/tool orchestration unless strictly required by shared assistant internals.

## Proposed design

### Response contract

Keep the successful interpretation response backward-compatible by retaining the current top-level draft fields and adding only an additive status field:

```json
{
  "description": "Coffee and bread",
  "amount": 2300,
  "category": "COMIDA",
  "status": "OK"
}
```

For uncertain but still expense-related prompts, return `200 OK` with nullable draft fields and `status: "INCOMPLETE"`:

```json
{
  "description": "Coffee",
  "amount": null,
  "category": null,
  "status": "INCOMPLETE"
}
```

For unrelated or semantically rejected prompts, do not return a draft. Return a structured assistant error:

- HTTP status: `422 Unprocessable Entity`
- code: `assistant_out_of_scope`

### Assistant model contract

Introduce an internal interpretation result, for example:

- `InterpretationResult(status, description, amount, category)` or equivalent DTO
- `InterpretationStatus`: `OK`, `INCOMPLETE`, `OUT_OF_SCOPE`

The controller maps `OUT_OF_SCOPE` to `AssistantSemanticRejectionException`/422, while `OK` and `INCOMPLETE` serialize as successful responses with the existing draft fields plus `status`.

### Input and prompt hardening

`AssistantInputValidator` should continue enforcing non-blank and max length, then add focused checks for this endpoint:

- minimum prompt length, e.g. at least 3 non-whitespace characters
- obvious instruction-override markers such as `ignore previous instructions`, `system prompt`, or similar small, reviewable markers

The interpretation system prompt should explicitly state that the model must only interpret personal expense prompts, must ignore instruction-override attempts, must not persist anything, and must produce the expected status/result shape.

### Rate limiting

Add an in-memory per-session limiter for `/transactions/interpret`, keyed by session id. Configuration should be externalized, for example:

- `ai.interpret.rate-limit.requests-per-minute`

This is intentionally a first-slice abuse/cost control. It is not a multi-node quota solution. The implementation should keep the limiter behind a small interface so a distributed backend can replace it later.

### Timeout and error hygiene

Configure a non-zero timeout for the interpretation AI call. Timeout and known provider/client failures should produce sanitized assistant errors:

- timeout: 502-class response with a distinct code such as `assistant_timeout`
- integration failure: existing `assistant_integration_error`, without leaking raw provider messages to clients

Operator logs may include exception details, but client responses must remain sanitized.

### Logging

Add structured outcome logs for interpretation attempts. Logs may include:

- prompt length
- truncated SHA-256 hash of the prompt
- latency in milliseconds
- outcome: `ok`, `incomplete`, `out_of_scope`, `validation_error`, `rate_limited`, `timeout`, `integration_error`

Logs must not include the raw prompt text.

## Affected areas

| Area | Expected change |
|------|-----------------|
| `TransactionController` | Consult validator/rate limiter, map interpretation statuses, emit headers. |
| `TransactionAssistantFacade` | Harden system prompt, parse result status, add timeout handling and safe logging. |
| `AssistantInputValidator` | Add minimum length and injection-marker validation. |
| `AssistantExceptionHandler` | Add 422/429 and timeout/error-code mappings. |
| `TransactionDraft` / response DTOs | Preserve fields; add additive `status` in successful interpret responses. |
| `TransactionAssistant` | Return a richer interpretation result internally. |
| New assistant exceptions | Add semantic rejection, rate limit, and timeout exceptions as needed. |
| Configuration | Add typed properties for rate limit and timeout knobs. |
| `openspec/specs/transaction-api/spec.md` | Add hardened `/transactions/interpret` scenarios. |
| Tests | Add failing tests before production changes, then implement to green. |

`openspec/specs/assistant-demo-api/spec.md` is expected to remain unchanged unless shared assistant error handling forces a narrowly scoped documentation update.

## TDD and review strategy

Strict TDD is active. Add failing tests first, then implement the smallest code needed to pass them. Use `./gradlew test` as the verification command.

Recommended RED tests:

1. Reject prompt below minimum length with `400 assistant_validation_error`.
2. Reject obvious prompt-injection marker with `400 assistant_validation_error`.
3. Return `422 assistant_out_of_scope` when the assistant signals `OUT_OF_SCOPE`.
4. Return `200` plus `status: INCOMPLETE` for partial expense drafts.
5. Return `429 assistant_rate_limited` and `Retry-After` after the configured per-session limit is exceeded.
6. Expose rate-limit headers on successful interpretation responses where practical.
7. Verify logs never contain raw prompt text.
8. Wrap timeout as a distinct assistant timeout exception/error code.
9. Pin the updated interpretation system prompt with the out-of-scope clause.
10. Preserve anonymous `401 Unauthorized` for `/transactions/interpret`.
11. Preserve the happy-path response fields and amount-in-centavos contract.

Keep this as a single PR if the diff remains close to the requested review budget (~800 changed lines). If implementation pushes beyond that budget, split only along a clean boundary: contract/tests first, then rate-limit/timeout/logging hardening.

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Existing clients break if response fields move under an envelope. | Keep `description`, `amount`, and `category` top-level; only add `status`. |
| Model does not reliably emit the new status. | Coerce valid drafts to `OK`, null/partial drafts to `INCOMPLETE`, and reserve `OUT_OF_SCOPE` for explicit model or server-side rejection. |
| In-memory rate limit is ineffective across multiple app instances. | Document as known limitation; keep a replaceable limiter abstraction. |
| Prompt-injection marker checks create false positives. | Keep the marker list small and focused on obvious instruction override attempts; return a clear validation error. |
| Sanitizing provider errors hides useful operator detail. | Keep detailed cause in server logs while returning sanitized client errors. |
| Timeout configuration may be awkward with the current Spring AI client. | Prefer Spring AI-supported timeout options; otherwise wrap with a small, testable timeout boundary. |
| Review workload grows beyond the single-PR target. | Avoid role changes, DB changes, and `/api/*` assistant rewrites; keep changes local to interpretation hardening. |

## Rollback plan

- Revert the OpenSpec change and the implementation PR as a unit if the additive response field or new error mappings cause unexpected client impact.
- Because no database migration is proposed, rollback is code/config only.
- If rate limiting causes operational friction, set the configured limit high or temporarily disable the limiter through configuration while keeping validation and logging hardening intact.
- If the model prompt change degrades extraction quality, revert only the prompt/status parsing portion while preserving safe logging, timeout, and validation tests where possible.

## Success criteria

- `/transactions/interpret` remains authenticated and path-compatible.
- Existing successful clients still receive top-level `description`, `amount`, and `category`.
- Successful responses include an additive `status` signal.
- Out-of-scope prompts return `422` with a stable assistant error code.
- Obvious malformed or injection-style prompts are rejected before the AI call.
- Excess per-session usage returns `429` with `Retry-After`.
- AI timeouts return sanitized 502-class assistant errors.
- Logs never contain raw prompt text.
- No new roles, DB changes, distributed rate-limit backend, or broad `/api/*` assistant changes are introduced.
- `./gradlew test` passes after the TDD implementation.
