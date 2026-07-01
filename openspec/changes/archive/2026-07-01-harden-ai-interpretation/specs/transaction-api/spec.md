# Delta for Transaction API

This delta hardens `POST /transactions/interpret` for personal-expense interpretation only. The existing top-level successful response fields (`description`, `amount`, `category`) and the existing `400` / `401` / `502` contract are preserved. All new behavior below is additive: a new `status` discriminator on successful responses, a `422` semantic-rejection path, a `429` rate-limit path with `Retry-After`, a distinct `assistant_timeout` 502 path, and stricter pre-AI input and logging hygiene.

## ADDED Requirements

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
