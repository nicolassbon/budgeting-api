# Transaction API Specification

## Purpose

Define the stable external contract for manual transaction creation/listing and transaction AI tool exposure during transaction-slice consolidation.

## Requirements

### Requirement: Create transactions

The system MUST keep `POST /transactions` compatible for creating a transaction from the existing JSON fields `description`, `category`, and `amount`.

#### Scenario: Create a transaction successfully

- GIVEN a JSON body with `description`, a supported `category`, and numeric `amount`
- WHEN the client calls `POST /transactions`
- THEN the response status SHALL be `201 Created`
- AND the response body SHALL include `id`, `description`, `category`, and `amount`

#### Scenario: Preserve category contract on create

- GIVEN a create request using an existing supported category value
- WHEN the client calls `POST /transactions`
- THEN the request SHALL be accepted using that category value unchanged
- AND the response SHALL report the created transaction category without renaming it

### Requirement: List transactions by category

The system MUST keep category-based listing available at `GET /transactions/{category}` and SHALL return transactions for the requested category in the existing response shape.

#### Scenario: List transactions for a category

- GIVEN stored transactions for a supported category
- WHEN the client calls `GET /transactions/{category}` with that category
- THEN the response status SHALL be `200 OK`
- AND the response body SHALL be a list of transaction objects with `id`, `description`, `category`, and `amount`

#### Scenario: Return an empty category list without contract changes

- GIVEN no stored transactions for a supported category
- WHEN the client calls `GET /transactions/{category}`
- THEN the response SHALL remain successful
- AND the response body SHALL be an empty list rather than a different shape

### Requirement: Preserve transaction AI tool-call exposure

The system MUST keep the transaction AI flow able to invoke the existing transaction tools for create and category listing, and MUST NOT rename or remove those tool-call capabilities in this change.

#### Scenario: AI create tool remains callable

- GIVEN the transaction AI flow needs to persist a transaction
- WHEN the assistant resolves available transaction tools
- THEN the `persist-transaction` tool SHALL remain exposed
- AND it SHALL keep accepting transaction description, amount, and category inputs

#### Scenario: AI listing tool remains callable

- GIVEN the transaction AI flow needs to retrieve transactions by category
- WHEN the assistant resolves available transaction tools
- THEN the `list-transactions-by-category` tool SHALL remain exposed
- AND it SHALL keep accepting a category input and returning transaction results for that category
