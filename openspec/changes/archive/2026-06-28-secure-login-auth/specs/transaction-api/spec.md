# Delta for Transaction API

## ADDED Requirements

### Requirement: Enforce authenticated transaction access

The system MUST require an authenticated session for transaction HTTP endpoints and transaction AI tool-backed persistence flows.

#### Scenario: Reject anonymous transaction access
- GIVEN no authenticated session
- WHEN the client calls `POST /transactions`, `GET /transactions/{category}`, `POST /transactions/ai`, or `POST /transactions/interpret`
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

#### Scenario: Confirmed AI save uses the session owner
- GIVEN an authenticated user confirms an interpreted expense through the supported persistence flow
- WHEN the transaction is saved
- THEN the created transaction SHALL belong to that authenticated user

## MODIFIED Requirements

### Requirement: Create transactions

The system MUST keep `POST /transactions` compatible for creating a transaction from the existing JSON fields `description`, `category`, and `amount`, and SHALL create the transaction for the authenticated user only.
(Previously: The endpoint contract was preserved but ownership was not defined.)

#### Scenario: Create a transaction successfully
- GIVEN an authenticated user and a JSON body with `description`, a supported `category`, and numeric `amount`
- WHEN the client calls `POST /transactions`
- THEN the response status SHALL be `201 Created`
- AND the response body SHALL include `id`, `description`, `category`, and `amount`

#### Scenario: Preserve category contract on create
- GIVEN an authenticated user submits an existing supported category value
- WHEN the client calls `POST /transactions`
- THEN the request SHALL be accepted using that category value unchanged
- AND the created transaction SHALL be owned by that authenticated user

### Requirement: List transactions by category

The system MUST keep category-based listing available at `GET /transactions/{category}` and SHALL return only the authenticated user's transactions for the requested category in the existing response shape.
(Previously: Category listing was preserved but not scoped to an authenticated owner.)

#### Scenario: List transactions for a category
- GIVEN an authenticated user has stored transactions for a supported category
- WHEN the client calls `GET /transactions/{category}` with that category
- THEN the response status SHALL be `200 OK`
- AND the response body SHALL be a list of that user's transaction objects with `id`, `description`, `category`, and `amount`

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
