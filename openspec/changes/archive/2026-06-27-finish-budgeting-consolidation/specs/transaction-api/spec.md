# Delta for transaction-api

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Create transactions

The system MUST keep `POST /transactions` compatible for creating a transaction from the existing JSON fields `description`, `category`, and `amount`, even if request-to-application DTO mapping is consolidated internally.
(Previously: The requirement preserved the HTTP create contract but did not constrain internal DTO consolidation.)

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

The system MUST keep category-based listing available at `GET /transactions/{category}` and SHALL return transactions for the requested category in the existing response shape, even if response/output mapping is consolidated internally.
(Previously: The requirement preserved list behavior but did not constrain internal mapping consolidation.)

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

### Requirement: Preserve transaction AI HTTP and tool-call exposure

The system MUST keep `POST /transactions/ai` and `POST /transactions/interpret` compatible while preserving the existing transaction AI tools for create and category listing, and MUST NOT rename or remove those endpoint or tool-call capabilities in this change.
(Previously: The requirement only preserved transaction AI tool-call exposure.)

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

#### Scenario: AI endpoint paths remain stable

- GIVEN existing clients call `POST /transactions/ai` or `POST /transactions/interpret`
- WHEN the consolidation is released
- THEN those paths SHALL remain available without renaming or moving
