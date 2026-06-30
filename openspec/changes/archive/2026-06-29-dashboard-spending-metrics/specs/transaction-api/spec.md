# Delta for Transaction API

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Create transactions

The system MUST keep `POST /transactions` compatible for creating a transaction from `description`, `category`, and `amount`, MAY accept an optional transaction date, and SHALL create the transaction for the authenticated user only.
(Previously: The endpoint accepted description, category, and amount without date support.)

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

### Requirement: Enforce authenticated transaction access

The system MUST require an authenticated session for transaction HTTP endpoints, now including the transaction history endpoint, and transaction AI tool-backed persistence flows.
(Previously: The requirement covered `POST /transactions`, `GET /transactions/{category}`, `POST /transactions/ai`, and `POST /transactions/interpret`.)

#### Scenario: Reject anonymous transaction access

- GIVEN no authenticated session
- WHEN the client calls `POST /transactions`, `GET /transactions/{category}`, the transaction history endpoint, `POST /transactions/ai`, or `POST /transactions/interpret`
- THEN the response SHALL be `401 Unauthorized`

## REMOVED Requirements

None.

## RENAMED Requirements

None.
