# Transaction API Specification

## Purpose

Define the stable external contract for manual transaction creation/listing and transaction AI tool exposure during transaction-slice consolidation.

## Requirements

### Requirement: Transaction date persistence and API response

The system MUST persist a transaction date/timestamp and expose it in transaction API responses.

#### Scenario: Create with explicit date

- GIVEN an authenticated user sends `POST /transactions` with an optional date
- WHEN the transaction is created
- THEN the response SHALL include the persisted date

#### Scenario: Create without date defaults to now

- GIVEN an authenticated user sends `POST /transactions` without a date
- WHEN the transaction is created
- THEN the system SHALL default the date to the current timestamp
- AND the response SHALL include the defaulted date

### Requirement: Create transactions

The system MUST keep `POST /transactions` compatible for creating a transaction from `description`, `category`, and `amount`, MAY accept an optional transaction date, and SHALL create the transaction for the authenticated user only.
(Previously: The endpoint accepted description, category, and amount without date support.)

The `amount` field in `POST /transactions` requests and in every transaction response is an **integer in centavos (1 peso = 100 centavos)**. Clients MUST divide the value by `100` to obtain pesos for display. The same unit applies to the `amount` returned by the `POST /transactions/interpret` draft endpoint.

#### Scenario: Create a transaction successfully

- GIVEN an authenticated user and a JSON body with `description`, a supported `category`, and numeric `amount`
- WHEN the client calls `POST /transactions`
- THEN the response status SHALL be `201 Created`
- AND the response body SHALL include `id`, `description`, `category`, `amount`, and `date`

#### Scenario: Preserve category contract on create

- GIVEN an authenticated user submits an existing supported category value
- WHEN the client calls `POST /transactions`
- THEN the request SHALL be accepted using that category value unchanged
- AND the created transaction SHALL be owned by that authenticated user

#### Scenario: Backward-compatible create without date

- GIVEN an authenticated user submits `description`, `category`, and `amount` without a date
- WHEN the client calls `POST /transactions`
- THEN the response status SHALL be `201 Created`
- AND the response SHALL include a defaulted date while preserving all existing fields

### Requirement: Owner-scoped transaction history endpoint

The system MUST expose an authenticated, owner-scoped transaction history endpoint with optional period and category filters and totals context.

#### Scenario: List all transactions for current user

- GIVEN an authenticated user has stored transactions
- WHEN the client calls the history endpoint without filters
- THEN the response SHALL be `200 OK`
- AND the body SHALL list that user's transactions with date, description, category, and amount

#### Scenario: Filter history by period

- GIVEN an authenticated user has transactions across multiple periods
- WHEN the client calls the history endpoint with period filters
- THEN the response SHALL include only transactions within the requested period

#### Scenario: Filter history by category

- GIVEN an authenticated user has transactions in multiple categories
- WHEN the client calls the history endpoint with a category filter
- THEN the response SHALL include only transactions matching that category

#### Scenario: History is owner-scoped

- GIVEN two users have transactions matching the same filter
- WHEN one user requests the history endpoint with filters
- THEN the returned list and totals SHALL include only that user's data

### Requirement: List transactions by category

The system MUST keep category-based listing available at `GET /transactions/{category}`, return only the authenticated user's transactions for the requested category in the existing response shape extended with the transaction date, and remain backward compatible for clients that ignore the new field.
(Previously: The response included `id`, `description`, `category`, and `amount` only.)

#### Scenario: List transactions for a category

- GIVEN an authenticated user has stored transactions for a supported category
- WHEN the client calls `GET /transactions/{category}` with that category
- THEN the response status SHALL be `200 OK`
- AND the response body SHALL be a list of that user's transaction objects with `id`, `description`, `category`, `amount`, and `date`

#### Scenario: Return an empty category list without contract changes

- GIVEN the authenticated user has no stored transactions for a supported category
- WHEN the client calls `GET /transactions/{category}`
- THEN the response SHALL remain successful
- AND the response body SHALL be an empty list rather than another user's data or a different shape

### Requirement: Preserve transaction AI HTTP and tool-call exposure

The system MUST keep `POST /transactions/ai` and `POST /transactions/interpret` compatible while preserving the existing transaction AI tools, and SHALL execute those flows only for the authenticated session owner.
(Previously: The requirement preserved endpoint and tool exposure without auth or owner scoping.)

#### Scenario: AI create tool remains callable
- GIVEN an authenticated transaction AI flow needs to persist a transaction
- WHEN the assistant resolves available transaction tools
- THEN the `persist-transaction` tool SHALL remain exposed
- AND persisted data SHALL belong to the authenticated session owner

#### Scenario: AI listing tool remains callable
- GIVEN an authenticated transaction AI flow needs to retrieve transactions by category
- WHEN the assistant resolves available transaction tools
- THEN the `list-transactions-by-category` tool SHALL remain exposed
- AND it SHALL return only the authenticated user's transaction results

#### Scenario: AI endpoint paths remain stable
- GIVEN existing clients call `POST /transactions/ai` or `POST /transactions/interpret`
- WHEN the auth change is released
- THEN those paths SHALL remain available without renaming or moving

### Requirement: Extract transaction AI orchestration behind a dedicated assistant seam

The system MUST keep `TransactionController` as a thin HTTP adapter for transaction AI endpoints while delegating transcription, prompt loading, tool registration, interpretation, and TTS orchestration to an assistant-focused component.

#### Scenario: Preserve `/transactions/ai` behavior during extraction

- GIVEN a multipart `file` request to `POST /transactions/ai`
- WHEN the AI transaction flow is executed after consolidation
- THEN the response SHALL remain `audio/mp3` with the existing attachment behavior
- AND the flow SHALL still use `src/main/resources/prompts/system-message.st` and transaction tools

#### Scenario: Preserve `/transactions/interpret` behavior during extraction

- GIVEN a JSON body with `prompt` for `POST /transactions/interpret`
- WHEN the interpretation flow is executed after consolidation
- THEN the response SHALL keep `description`, `amount`, and `category` fields
- AND the returned `amount` SHALL be an integer in centavos, consistent with the persisted transaction API

### Requirement: Prove consolidation with focused compatibility tests

The system MUST include focused tests that characterize transaction AI endpoint compatibility and transaction service/tool contracts before and after consolidation.

#### Scenario: Guard HTTP and tool compatibility with focused tests

- GIVEN the consolidation change is implemented
- WHEN the focused controller and service/tool tests run
- THEN they SHALL verify stable endpoint shapes, tool names, and DTO field names

#### Scenario: Avoid reliance on live-provider rewrites

- GIVEN OpenAI-backed tests are environment-gated
- WHEN compatibility for this change is verified
- THEN the change SHALL rely on focused deterministic tests rather than expanding live-provider coverage

### Requirement: Enforce authenticated transaction access

The system MUST require an authenticated session for transaction HTTP endpoints, now including the transaction history endpoint, and transaction AI tool-backed persistence flows.
(Previously: The requirement covered `POST /transactions`, `GET /transactions/{category}`, `POST /transactions/ai`, and `POST /transactions/interpret`.)

#### Scenario: Reject anonymous transaction access

- GIVEN no authenticated session
- WHEN the client calls `POST /transactions`, `GET /transactions/{category}`, the transaction history endpoint, `POST /transactions/ai`, or `POST /transactions/interpret`
- THEN the response SHALL be `401 Unauthorized`

### Requirement: Enforce per-user transaction ownership

The system MUST scope transaction reads and writes to the authenticated user. Current transaction routes do not expose a transaction-id target for cross-user mutation or retrieval; if such a route is added later, attempts to act on another user's transaction data MUST return `403 Forbidden`.

#### Scenario: Category listing is owner-scoped
- GIVEN two authenticated users with transactions in the same category
- WHEN one user lists that category
- THEN the response SHALL contain only that user's transactions

#### Scenario: Cross-user access is not exposed by the current route shape
- GIVEN two authenticated users with transactions in the same category
- WHEN one user calls the currently supported category listing route
- THEN the response SHALL NOT include the other user's transaction data
- AND no current route SHALL allow that user to target or mutate the other user's transaction by id

#### Scenario: Future targeted cross-user access is forbidden
- GIVEN a future route targets transaction data owned by another user
- WHEN the authenticated user attempts that action
- THEN the response SHALL be `403 Forbidden`
- AND no other user's transaction data SHALL be returned or mutated

### Requirement: Confirm AI-interpreted persistence before save

The system MUST require authenticated confirmation before an AI-interpreted expense is persisted for a user.

#### Scenario: Interpretation does not persist by itself
- GIVEN an authenticated user submits `POST /transactions/interpret`
- WHEN the system returns interpreted expense data
- THEN the interpretation result SHALL be returned without creating a stored transaction
- AND the returned `amount` SHALL be an integer in centavos (same unit as the persisted transaction API)

#### Scenario: Confirmed AI save uses the session owner
- GIVEN an authenticated user confirms an interpreted expense through the supported persistence flow
- WHEN the transaction is saved
- THEN the created transaction SHALL belong to that authenticated user

### Requirement: Add additive status field to /transactions/interpret response

The system MUST return `/transactions/interpret` results with the existing top-level `description`, `amount`, and `category` draft fields and MUST add an additive top-level `status` field whose value is one of `OK` or `INCOMPLETE` for successful interpretations. The system MUST treat a non-null, valid draft as `OK` and a non-empty `description` with null `amount` and/or null `category` as `INCOMPLETE`. The system MUST NOT remove, rename, or reposition the existing draft fields, so clients that ignore `status` continue to work unchanged.

#### Scenario: Successful interpretation returns status OK

- GIVEN an authenticated user submits a JSON body with a valid expense `prompt`
- WHEN the AI interpretation flow returns a non-null `description`, `amount`, and `category`
- THEN the response status SHALL be `200 OK`
- AND the response body SHALL include `description`, `amount`, `category`, and `status: "OK"` at the top level
- AND `amount` SHALL remain an integer in centavos

#### Scenario: Partial interpretation returns status INCOMPLETE

- GIVEN an authenticated user submits a JSON body with a `prompt` whose expense fields cannot all be resolved
- WHEN the AI returns a non-empty `description` but null `amount` and null `category`
- THEN the response status SHALL be `200 OK`
- AND the response body SHALL include `status: "INCOMPLETE"`
- AND `amount` and `category` SHALL remain in the body, both `null`

### Requirement: Reject malformed and injection-style prompts before AI call

The system MUST validate the `/transactions/interpret` prompt before invoking the AI model. The system MUST reject prompts whose trimmed non-blank length is below a small configured minimum and MUST reject prompts that contain a small, reviewable set of obvious instruction-override or prompt-injection markers. For both rejection reasons the system MUST return `400 Bad Request` with the existing `assistant_validation_error` error code and MUST NOT call the AI model.

#### Scenario: Reject prompt below minimum length

- GIVEN an authenticated user submits a `prompt` whose trimmed non-blank content is below the configured minimum (for example fewer than 3 non-whitespace characters)
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `400 Bad Request`
- AND the response body SHALL include the `assistant_validation_error` error code
- AND the AI model SHALL NOT be invoked

#### Scenario: Reject prompt matching an injection marker

- GIVEN an authenticated user submits a `prompt` that contains a recognized instruction-override marker such as `ignore previous instructions`, `system prompt`, or an equivalent small, reviewable marker
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `400 Bad Request`
- AND the response body SHALL include the `assistant_validation_error` error code
- AND the AI model SHALL NOT be invoked

### Requirement: Map out-of-scope prompts to 422 assistant_out_of_scope

The system MUST treat prompts that the AI interpretation flow classifies as not-a-personal-expense as a structured assistant rejection rather than a successful draft. The system MUST return `422 Unprocessable Entity` with the new `assistant_out_of_scope` error code. The system MUST NOT return a `TransactionDraft` payload for out-of-scope prompts.

#### Scenario: Reject out-of-scope prompt with 422

- GIVEN an authenticated user submits a `prompt` that the AI classification marks as not a personal expense
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `422 Unprocessable Entity`
- AND the response body SHALL include the `assistant_out_of_scope` error code
- AND the response body SHALL NOT include a `TransactionDraft` payload

### Requirement: Enforce per-session rate limit on /transactions/interpret

The system MUST apply a per-session, in-memory rate limit to `POST /transactions/interpret` requests. The system MUST return `429 Too Many Requests` with the new `assistant_rate_limited` error code and a `Retry-After` response header when a session exceeds the configured limit, and MUST NOT invoke the AI model for rejected calls. The system MUST continue to require an authenticated session; anonymous callers SHALL be rejected with `401 Unauthorized` before any rate-limit decision is made, and the rate-limit counter SHALL NOT be incremented for unauthenticated requests.

#### Scenario: Reject excess calls with 429 and Retry-After

- GIVEN an authenticated session has already used all configured interpretation requests for the current window
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `429 Too Many Requests`
- AND the response SHALL include a `Retry-After` header
- AND the response body SHALL include the `assistant_rate_limited` error code
- AND the AI model SHALL NOT be invoked

#### Scenario: Anonymous callers are rejected before rate limiting

- GIVEN no authenticated session
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `401 Unauthorized`
- AND the rate-limit counter SHALL NOT be incremented for the unauthenticated request

### Requirement: Expose rate-limit telemetry headers on interpretation responses

The system SHOULD expose informative `RateLimit-*` response headers on successful `/transactions/interpret` responses so that clients can observe their remaining quota for the current session. These headers SHALL be advisory only and SHALL NOT replace the `429` body response defined in the rate-limit requirement.

#### Scenario: Successful interpretation includes RateLimit-* headers

- GIVEN an authenticated session that has not yet exceeded the configured rate limit
- WHEN the client calls `POST /transactions/interpret` and the system returns `200 OK`
- THEN the response SHOULD include one or more `RateLimit-*` advisory headers (for example `RateLimit-Remaining`) indicating the session's remaining quota for the current window

### Requirement: Distinguish AI timeouts from generic integration failures

The system MUST apply a non-zero timeout to the AI interpretation call and MUST treat timeouts as a distinct assistant failure from other provider/transport failures. The system MUST return `502 Bad Gateway` with the new `assistant_timeout` error code for timeouts and the existing `assistant_integration_error` code for other provider or transport failures. The system MUST NOT leak raw provider error messages, stack traces, or other internal details to the client response; the original cause SHALL be retained in server logs only.

#### Scenario: Return sanitized 502 with assistant_timeout on AI timeout

- GIVEN the AI interpretation call exceeds the configured timeout
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `502 Bad Gateway`
- AND the response body SHALL include the `assistant_timeout` error code
- AND the response body SHALL NOT include the raw provider message, stack trace, or other internal details

#### Scenario: Return sanitized 502 with assistant_integration_error on other provider failure

- GIVEN the AI interpretation call fails for a non-timeout, non-validation reason (for example a provider or transport error)
- WHEN the client calls `POST /transactions/interpret`
- THEN the response status SHALL be `502 Bad Gateway`
- AND the response body SHALL include the `assistant_integration_error` error code
- AND the response body SHALL NOT include the raw provider message, stack trace, or other internal details

### Requirement: Log interpretation telemetry without raw prompt text

The system MUST emit structured log events for `/transactions/interpret` attempts that include the prompt length, a truncated SHA-256 hash of the prompt (for example the first 8 hex characters), latency in milliseconds, and an outcome label drawn from `ok`, `incomplete`, `out_of_scope`, `validation_error`, `rate_limited`, `timeout`, and `integration_error`. The system MUST NOT include the raw prompt text, the full hash, or other PII in any log event.

#### Scenario: Logs include telemetry but never the raw prompt

- GIVEN an authenticated user calls `POST /transactions/interpret` with any `prompt` value
- WHEN the system processes the request through validation, rate limiting, AI invocation, and outcome mapping
- THEN the system SHALL emit log events that include the prompt length, a truncated SHA-256 hash, latency in milliseconds, and an outcome label
- AND no log event SHALL contain the raw prompt text, the full hash, or other PII
- AND the outcome label SHALL match the actual interpretation result for that request
