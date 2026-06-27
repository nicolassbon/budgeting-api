# Tasks: Transaction Consolidation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250-380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR with 3 work units |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Lock HTTP and tool contracts with failing tests | PR 1 | MockMvc + reflection tests first |
| 2 | Replace thin use cases with `application/TransactionService.java` | PR 1 | Delete old use cases; keep tool names |
| 3 | Rewire controller and confirm slice regression checks | PR 1 | Run targeted tests, then `./gradlew test` |

## Phase 1: Contract Locking

- [x] 1.1 Add `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` covering `POST /transactions` 201/body and `GET /transactions/{category}` 200/empty-list scenarios from the spec.
- [x] 1.2 Add `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` asserting `@Tool` names/descriptions, `PersistTransactionInput` `@ToolParam` fields, and `TransactionOutput` record fields remain unchanged.
- [x] 1.3 Add `src/test/java/dio/budgeting/application/TransactionServiceTest.java` with RED cases for create/list repository delegation and output mapping using a fake `TransactionRepository`.

## Phase 2: Application Consolidation

- [x] 2.1 Create `src/main/java/dio/budgeting/application/TransactionService.java` with `create(PersistTransactionInput)` and `findAllByCategory(Category)` preserving current tool annotations and repository orchestration.
- [x] 2.2 Update `src/main/java/dio/budgeting/application/output/TransactionOutput.java` and `.../input/PersistTransactionInput.java` only as needed to keep boundary-local mapping explicit and contract-stable.
- [x] 2.3 Delete `src/main/java/dio/budgeting/application/PersistTransactionUseCase.java` and `.../ListTransactionsByCategoryUseCase.java` after `TransactionServiceTest` is green.

## Phase 3: HTTP Wiring

- [x] 3.1 Update `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` to inject `TransactionService`, register it in `defaultTools(...)`, and keep `/transactions/ai` orchestration untouched.
- [x] 3.2 Trim only avoidable pass-through mapping in `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` and `.../response/TransactionResponse.java` without changing JSON field names.
- [x] 3.3 Adjust `src/main/java/dio/budgeting/infraestructure/persistence/repository/JpaTransactionRepository.java` imports only if compilation requires it; no schema/entity change.

## Phase 4: Verification

- [x] 4.1 Run `./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.application.TransactionToolContractTest"` and fix regressions before broader checks.
- [x] 4.2 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests" --tests "dio.budgeting.FlywayMigrationIT"` to confirm wiring and persistence stability.
- [x] 4.3 Run `./gradlew test`; if env-gated AI tests block completion, document that outcome in the verify phase rather than broadening this slice.
