# Weekly Budget Specification

## Purpose

Define the MVP contract for persisting the authenticated user's optional weekly budget amount without introducing a generic preferences system.

## Acceptance Criteria

- `GET /auth/me/weekly-budget` returns `200 OK` with `{ "amount": number | null }` for an authenticated user.
- When no weekly budget has been stored, the response body returns `{"amount": null}` rather than `404`.
- `PUT /auth/me/weekly-budget` accepts `{ "amount": number }` or `{ "amount": null }` and the stored value is returned by subsequent `GET` requests for the same authenticated user.
- Unauthenticated `GET` and `PUT` requests are rejected consistently with existing authenticated endpoint behavior.
- Invalid `PUT` payloads are rejected without changing the stored weekly budget value.

## Requirements

### Requirement: Read and write the current user's weekly budget

The system MUST expose authenticated `GET /auth/me/weekly-budget` and `PUT /auth/me/weekly-budget` endpoints that read and update only the current user's persisted weekly budget amount, and both endpoints SHALL use the response body shape `{ "amount": number | null }`.

#### Scenario: Missing budget returns null
- GIVEN an authenticated user with no stored weekly budget amount
- WHEN the client calls `GET /auth/me/weekly-budget`
- THEN the response SHALL be `200 OK` with `{ "amount": null }`

#### Scenario: Persisted budget round-trips through PUT and GET
- GIVEN an authenticated user
- WHEN the client sends `PUT /auth/me/weekly-budget` with `{ "amount": 12500.50 }` and later calls `GET /auth/me/weekly-budget`
- THEN the GET response SHALL be `200 OK` with `{ "amount": 12500.50 }`

#### Scenario: Clearing the budget persists null
- GIVEN an authenticated user with a stored weekly budget amount
- WHEN the client sends `PUT /auth/me/weekly-budget` with `{ "amount": null }`
- THEN a subsequent `GET /auth/me/weekly-budget` SHALL return `{ "amount": null }`

### Requirement: Preserve existing auth behavior for weekly budget endpoints

The system MUST preserve the existing authentication behavior for protected routes on both weekly budget endpoints. Unauthenticated access SHALL be rejected consistently with the current protected endpoint contract, and authenticated state-changing requests SHALL continue using the existing auth protections.

#### Scenario: Reject anonymous read access
- GIVEN no authenticated session
- WHEN the client calls `GET /auth/me/weekly-budget`
- THEN the response SHALL be `401 Unauthorized`

#### Scenario: Reject anonymous write access
- GIVEN no authenticated session
- WHEN the client calls `PUT /auth/me/weekly-budget`
- THEN the response SHALL be rejected consistently with the existing protected write behavior

### Requirement: Reject invalid weekly budget update payloads

The system MUST reject malformed or contract-breaking `PUT /auth/me/weekly-budget` payloads according to existing request validation conventions, and MUST NOT persist a partial or replacement value when the request is invalid.

#### Scenario: Reject non-numeric amount values
- GIVEN an authenticated user with any current weekly budget value
- WHEN the client sends `PUT /auth/me/weekly-budget` with an `amount` value that is not numeric or null
- THEN the response SHALL be `400 Bad Request`
- AND a later `GET /auth/me/weekly-budget` SHALL return the previously stored value unchanged
