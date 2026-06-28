# Tasks: Finish Budgeting Consolidation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 280-420 |
| 400-line budget risk | Medium |
| 800-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr-default |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium
800-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Finish facade extraction, DTO cleanup, and focused tests | PR 1 | Single PR; keep tests/docs with code |

## Phase 1: Characterization / RED

- [x] 1.1 Extend `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` to lock `/transactions/ai` audio headers/body flow and `/transactions/interpret` JSON fields before refactor.
- [x] 1.2 Extend `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` to keep Spring AI tool names, `PersistTransactionInput` field metadata, and `TransactionOutput`/`TransactionResponse` naming boundaries explicit.
- [x] 1.3 Add `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java` covering transcription → tool-enabled chat → TTS and interpret chat sequencing with mocks.

## Phase 2: Extraction / GREEN

- [x] 2.1 Create `src/main/java/dio/budgeting/assistant/TransactionAssistant.java` and `src/main/java/dio/budgeting/assistant/TransactionAssistantFacade.java` to own prompt loading, chat-client building, transcription, interpretation, and MP3 response assembly.
- [x] 2.2 Refactor `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` so CRUD endpoints stay intact while `/transactions/ai` and `/transactions/interpret` delegate to the assistant boundary.
- [x] 2.3 Move `TransactionDraft` to the assistant boundary or re-export it safely, preserving the exact `description`, `amount`, and `category` JSON contract.

## Phase 3: DTO Consolidation / REFACTOR

- [x] 3.1 Update `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` to become the primary application-owned constructor/factory without direct HTTP coupling.
- [x] 3.2 Update `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` to stay a thin HTTP adapter over the application input shape.
- [x] 3.3 Update `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` and verify `src/main/java/dio/budgeting/application/output/TransactionOutput.java` still separate HTTP `amount` from tool/output `value`.

## Phase 4: Verification

- [x] 4.1 Run focused tests for `TransactionControllerTest`, `TransactionAssistantFacadeTest`, and `TransactionToolContractTest` first, fixing regressions before broader runs.
- [x] 4.2 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` to prove wiring safety without expanding live-provider scope.
- [x] 4.3 Run `./gradlew test`; if `OPENAI_API_KEY` is present, also check `./gradlew test --tests "dio.budgeting.ToolCallingIT"` for unchanged env-gated integration behavior.
