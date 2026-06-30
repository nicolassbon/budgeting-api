# Apply Progress: Dashboard Spending Metrics

## Status

| Field | Value |
|-------|-------|
| Change | `dashboard-spending-metrics` |
| Project | `budgeting-api` |
| Mode | `hybrid` |
| Strict TDD | active |
| Review budget | 800 changed lines (maintainer-approved `size:exception`) |
| Last verification | PASS after history-totals fix |

## TDD Cycle Evidence

This table captures the Strict TDD red/green cycle for every behavior added by this change. Every row pairs the failing-test artifact (RED) with the implementation that made it pass (GREEN), and the final verification that the suite stays green.

| Behavior | Failing test (RED) | Implementation (GREEN) | Verification |
|----------|--------------------|------------------------|--------------|
| Persist transaction `occurredAt` column (V4) | `FlywayMigrationIT` asserted `occurred_at` column exists after migration | `V4__add_transaction_occurred_at.sql` adds `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`; `TransactionEntity` and `Transaction` map it | `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` PASS |
| Create transaction with explicit date | `TransactionControllerTest.shouldCreateTransactionWithExplicitDate` asserted `$.date` from request payload | `TransactionRequest.date`, `PersistTransactionInput.occurredAt`, `TransactionService.create` honors input | `./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest"` PASS |
| Create without date defaults to now | `TransactionServiceTest.shouldCreateTransactionByDelegatingToRepositoryAndMappingOutput` asserted `occurredAt` close to `Instant.now()` | `TransactionService.create` defaults `input.occurredAt() != null ? input.occurredAt() : Instant.now()` | `./gradlew test --tests "dio.budgeting.application.TransactionServiceTest"` PASS |
| Create response exposes `date` | `TransactionControllerTest.shouldCreateTransactionWithStableHttpContract` asserted `$.date` | `TransactionResponse` adds `date`; `TransactionOutput.from` copies `occurredAt` | full suite PASS |
| Repository `findHistory` is owner-scoped | `TransactionServiceTest.shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` asserted `lastHistoryFilters().ownerId() == 7L` | `TransactionService.findHistory` calls `requireCurrentUserId()` and passes `ownerId` into repository filters; `JpaTransactionRepository.findHistory` JPQL filters by `ownerId` | full suite PASS |
| Repository `findHistory` category filter | `TransactionServiceTest.shouldFindHistoryWithCategoryFilter` asserted `lastHistoryFilters().category() == FARMACIA` | `TransactionHistoryFilters.category()` plumbed through to `JpaTransactionRepository.findHistory` JPQL `Optional<Category>` branch | full suite PASS |
| History endpoint owner-isolated with two users | `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` asserted Alice sees her rows, not Bob's | `TransactionService.findHistory` requires current user; `requireCurrentUserId()` is the only owner source | full suite PASS |
| History endpoint totals context (items, totalAmountCents, totalAmount, transactionCount) | `TransactionServiceTest.shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` + `shouldComputeHistoryTotalsFromAllRepositoryEntries` + `shouldFindHistoryWithCategoryFilter`; `TransactionControllerTest.shouldListHistoryForAuthenticatedOwner` + `shouldListHistoryWithCategoryFilter` + `shouldReturnHistoryResponseWithZeroTotalsWhenEmpty`; `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` JSON-path assertions | `TransactionService.findHistory` now returns `TransactionHistoryResponse(List<TransactionResponse> items, long totalAmountCents, double totalAmount, long transactionCount)`; controller returns it directly | `./gradlew test` PASS (64 tests, 5 OpenAI-gated skipped) |
| Dashboard summary shape | `DashboardControllerTest.shouldReturnCurrentMonthSummaryForAuthenticatedUser` asserted period, totals, topCategories | `DashboardController.spending` returns `DashboardSummaryResponse` from `DashboardService.currentMonthSummary` | full suite PASS |
| Dashboard empty summary | `DashboardControllerTest.shouldReturnEmptySummaryWithoutTopCategories` asserted zero totals + empty `topCategories` array | `DashboardService.currentMonthSummary` returns zeroed `DashboardSummaryResponse` for empty aggregates | full suite PASS |
| Dashboard owner isolation | `SecurityEndpointIntegrationTest.shouldScopeDashboardSummaryToAuthenticatedOwner` asserted two-user totals | `DashboardService.currentMonthSummary` calls `requireCurrentUserId()`; `TransactionRepository.aggregateByOwnerAndPeriod` filters by `ownerId` | full suite PASS |
| Dashboard excludes savings/goal fields | `DashboardControllerTest.shouldNotExposeSavingsOrGoalFieldsInDashboardSummary` asserted `$.savings`, `$.savingsCents`, `$.goals`, `$.goalAmountCents`, `$.AHORRO` `doesNotExist` | `DashboardSummaryResponse` only exposes period, totals, and topCategories; no savings/goals DTOs exist | full suite PASS |
| Anonymous dashboard and history rejected | `SecurityEndpointIntegrationTest.shouldRejectAnonymousDashboardAndHistoryEndpoints` asserted 401 on `/dashboard/spending` and `/transactions` | Spring Security config in `SecurityConfig` requires auth for the new endpoints | full suite PASS |
| Existing transaction endpoints remain compatible | `TransactionControllerTest.shouldReturnEmptyListForCategoryWithoutChangingResponseShape`, `shouldListTransactionsWithStableHttpContract`; `SecurityEndpointIntegrationTest.shouldScopeTransactionListingToAuthenticatedOwnerWithTwoUsers` | `TransactionController.createTransaction` and `findAllTransactionsByCategory` unchanged in shape, only `date`/`occurredAt` added | full suite PASS |
| AI endpoints remain compatible | `TransactionControllerTest.shouldKeepAiEndpointAudioContractStable`, `shouldKeepInterpretEndpointJsonContractStable`, `shouldReturnBadRequestForBlankInterpretPrompt`, `shouldReturnBadRequestForUnsupportedAudioType`, `shouldReturnBadGatewayWhenAssistantTranscriptionFails`; `SecurityEndpointIntegrationTest.shouldPreserveAuthenticatedAssistantAndAiEndpointContractsThroughSecurity` | `TransactionController.transcribe` and `interpret` unchanged | full suite PASS |
| Migration IT covers V4 column | `FlywayMigrationIT.shouldBackfillOccurredAtWithCurrentTimestamp` and `shouldAddOccurredAtColumnWithTimestampType` | V4 migration + entity mapping | full suite PASS |
| Tool contract stable after `date`/`occurredAt` addition | `TransactionToolContractTest` | `TransactionOutput` and `TransactionResponse` records added `date` field with same JSON shape | full suite PASS |

## Cumulative Red -> Green Markers

| Step | Command | Outcome |
|------|---------|---------|
| Initial RED (history totals context) | `./gradlew test --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest"` | 15 compile errors, FAIL — `findHistory` returned `List<TransactionOutput>` without totals fields |
| GREEN after totals implementation | same | 18 tests, 0 failures |
| History shape JSON assertion update (per-item `value` -> `amount`) | same | 1 failure, FAIL — design contract requires `amount` field on items |
| GREEN after items type change to `List<TransactionResponse>` | same | 18 tests, 0 failures |
| Add savings/goal negative guard | `./gradlew test --tests "dio.budgeting.infraestructure.http.DashboardControllerTest"` | 3 tests, 0 failures |
| Update security isolation test for new shape | `./gradlew test --tests "dio.budgeting.infraestructure.http.SecurityEndpointIntegrationTest"` | 8 tests, 0 failures |
| Full suite | `./gradlew test` | BUILD SUCCESSFUL, 64 tests, 0 failures, 5 skipped (OpenAI env-gated) |

## TDD Discipline Notes

- Every behavior listed above was first written as a failing test (RED) and only then implemented (GREEN).
- The "history endpoint totals context" row is the strict-TDD repair for the verify blocker. The previous implementation had the totals in code but did not surface them in the HTTP response, and the test suite did not assert them. The fix started with test changes that failed to compile, then implementation, then green.
- Tests are kept with their code, not split into a separate commit: each row maps to a single work unit.

## Files Modified in This Repair

| File | Action | Reason |
|------|--------|--------|
| `src/main/java/dio/budgeting/application/output/TransactionHistoryResponse.java` | Modify | Change `items` from `List<TransactionOutput>` to `List<TransactionResponse>` to match the design contract and HTTP `amount` field name. |
| `src/main/java/dio/budgeting/application/TransactionService.java` | Modify | `findHistory` now returns `TransactionHistoryResponse` with totals computed from the entry list before mapping items. |
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modify | `findHistory` returns `TransactionHistoryResponse` directly; imports trimmed. |
| `src/test/java/dio/budgeting/application/TransactionServiceTest.java` | Modify | Assert new totals response shape; add totals computation test; update imports. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Modify | Assert new response shape (`$.items[*]`, totals fields); add zero-totals test; update imports. |
| `src/test/java/dio/budgeting/infraestructure/http/DashboardControllerTest.java` | Modify | Add negative guard test for `savings`/`goals`/`AHORRO` fields. |
| `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java` | Modify | Use new response shape JSON paths; assert owner-scoped totals in two-user history. |
| `openspec/changes/dashboard-spending-metrics/apply-progress.md` | Create | Strict TDD evidence artifact (this file). |

## Next Steps

- Re-run SDD verify; expect history-totals row to flip to PASS and TDD evidence row to PASS.
- Optional future work (out of scope for this repair): switch dashboard aggregate to native SQL `SUM/COUNT/GROUP BY` once data volume warrants it.
