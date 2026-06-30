# Verification Report: Dashboard Spending Metrics

## Change

- Change ID: `dashboard-spending-metrics`
- Project: `budgeting-api`
- Mode: `hybrid`
- Strict TDD: active
- Test runner: `./gradlew test`
- Skill resolution: `injected`

## Final Verdict

**PASS WITH WARNINGS**

The previous blockers are resolved: `GET /transactions` now returns the history response shape with `items`, `totalAmountCents`, `totalAmount`, and `transactionCount`; apply progress now includes Strict TDD cycle evidence; and the full Gradle suite passes when rerun.

## Completeness

| Dimension | Result | Evidence |
|---|---:|---|
| Tasks checked | ✅ | OpenSpec `tasks.md` marks 22/22 tasks complete. Engram task artifact was read as the original planning baseline. |
| Source implementation present | ✅ | `TransactionController.findHistory` returns `TransactionHistoryResponse`; `TransactionService.findHistory` computes totals from owner-scoped entries. |
| Spec compliance | ✅ | Required dashboard/history/create/auth scenarios have passing covering tests. |
| Design coherence | ⚠️ | Owner-scoped reads are implemented, but dashboard aggregation still aggregates in memory after fetching owner-filtered rows instead of using database-side aggregate projections. |
| Strict TDD evidence | ✅ | OpenSpec `apply-progress.md` contains `## TDD Cycle Evidence`; Engram apply-progress contains merged TDD evidence and repair red/green markers. |

## Build / Test Evidence

| Command | Outcome | Notes |
|---|---:|---|
| `./gradlew test` | ✅ PASS | Initial run was Gradle `UP-TO-DATE` in 21s. |
| `./gradlew test --rerun-tasks` | ✅ PASS | BUILD SUCCESSFUL in 55s; 67 tests, 62 passed, 5 skipped, 0 failures, 0 errors. |

Skipped tests are OpenAI environment-gated integration tests: `OpenAiChatClientIT`, `OpenAiChatModelIT`, `OpenAiSpeechModelIT`, `OpenAiTranscriptionModelIT`, and `ToolCallingIT`.

## Spec Compliance Matrix

| Requirement / Scenario | Status | Runtime Evidence | Notes |
|---|---:|---|---|
| Dashboard summary with transactions | ✅ COMPLIANT | `DashboardControllerTest.shouldReturnCurrentMonthSummaryForAuthenticatedUser`, `DashboardServiceTest.shouldBuildDashboardSummaryForCurrentMonthScopedToAuthenticatedOwner`, `SecurityEndpointIntegrationTest.shouldScopeDashboardSummaryToAuthenticatedOwner` | Includes period, total, count, and category totals. |
| Empty dashboard summary | ✅ COMPLIANT | `DashboardControllerTest.shouldReturnEmptySummaryWithoutTopCategories`, `DashboardServiceTest.shouldReturnEmptySummaryWhenOwnerHasNoTransactionsInCurrentPeriod` | Zero totals and empty category breakdown covered. |
| Dashboard excludes other users | ✅ COMPLIANT | `SecurityEndpointIntegrationTest.shouldScopeDashboardSummaryToAuthenticatedOwner` | Two-user isolation covered. |
| Anonymous dashboard rejected | ✅ COMPLIANT | `SecurityEndpointIntegrationTest.shouldRejectAnonymousDashboardAndHistoryEndpoints` | 401 covered. |
| Expense-only dashboard / no savings fields | ✅ COMPLIANT | `DashboardControllerTest.shouldNotExposeSavingsOrGoalFieldsInDashboardSummary` | Explicit negative assertions for savings/goals/`AHORRO`. |
| Period grouping uses transaction date | ✅ COMPLIANT | `DashboardServiceTest.shouldRequestAggregateWithUtcStartAndExclusiveNextMonthStart`, `SecurityEndpointIntegrationTest.shouldScopeDashboardSummaryToAuthenticatedOwner` | UTC current-month bounds and persisted date path covered. |
| Create with explicit date | ✅ COMPLIANT | `TransactionControllerTest.shouldCreateTransactionWithExplicitDate`, `TransactionServiceTest.shouldCreateTransactionWithExplicitOccurredAt` | Date propagated. |
| Create without date defaults to now | ✅ COMPLIANT | `TransactionServiceTest.shouldCreateTransactionByDelegatingToRepositoryAndMappingOutput`, `SecurityEndpointIntegrationTest.shouldDefaultCreatedTransactionToCurrentTimestampWhenDateOmitted` | Default timestamp covered. |
| History lists current-user transactions | ✅ COMPLIANT | `TransactionControllerTest.shouldListHistoryForAuthenticatedOwner`, `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` | New response shape verified. |
| History filters by period | ✅ COMPLIANT | `TransactionControllerTest.shouldListHistoryForAuthenticatedOwner`, `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` | Query params map into history filters and runtime requests pass. |
| History filters by category | ✅ COMPLIANT | `TransactionControllerTest.shouldListHistoryWithCategoryFilter`, `TransactionServiceTest.shouldFindHistoryWithCategoryFilter`, `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` | Category filter covered. |
| History includes totals context | ✅ COMPLIANT | `TransactionControllerTest.shouldListHistoryForAuthenticatedOwner`, `shouldListHistoryWithCategoryFilter`, `shouldReturnHistoryResponseWithZeroTotalsWhenEmpty`; `TransactionServiceTest.shouldComputeHistoryTotalsFromAllRepositoryEntries` | `GET /transactions` returns `items`, `totalAmountCents`, `totalAmount`, `transactionCount`. |
| History owner-scoped totals | ✅ COMPLIANT | `SecurityEndpointIntegrationTest.shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers` | Alice receives one row and totals excluding Bob. |
| Create transaction remains compatible | ✅ COMPLIANT | `TransactionControllerTest.shouldCreateTransactionWithStableHttpContract`, `SecurityEndpointIntegrationTest.shouldScopeTransactionListingToAuthenticatedOwnerWithTwoUsers` | Existing fields preserved and `date` added. |
| Category listing remains compatible with date | ✅ COMPLIANT | `TransactionControllerTest.shouldListTransactionsWithStableHttpContract`, `shouldReturnEmptyListForCategoryWithoutChangingResponseShape` | Existing route shape preserved. |
| Anonymous transaction endpoints rejected | ✅ COMPLIANT | `SecurityEndpointIntegrationTest.shouldRejectAnonymousProtectedPostAndMultipartEndpoints`, `shouldRejectAnonymousDashboardAndHistoryEndpoints` | Includes history endpoint. |

**Compliance summary**: 16/16 scenarios compliant.

## Correctness

| Requirement | Status | Evidence |
|---|---:|---|
| `GET /transactions` history response shape | ✅ | `TransactionController.findHistory` returns `TransactionHistoryResponse`; controller tests assert `$.items`, `$.totalAmountCents`, `$.totalAmount`, and `$.transactionCount`. |
| Owner-scoped history totals | ✅ | `TransactionService.findHistory` injects authenticated owner into filters; security integration test asserts Bob is excluded from Alice's list and totals. |
| Strict TDD apply evidence present | ✅ | `openspec/changes/dashboard-spending-metrics/apply-progress.md` lines 14-48 contain TDD evidence and cumulative red/green markers. |

## Design Coherence

| Design Decision | Status | Evidence |
|---|---:|---|
| Separate `DashboardService`/`DashboardController` | ✅ | Implemented and tested. |
| All dashboard/history reads require authenticated owner | ✅ | `DashboardService` and `TransactionService.findHistory` call `requireCurrentUserId()`. |
| Persist `Instant occurredAt` as `TIMESTAMPTZ` | ✅ | Entity mapping and V4 migration present; Flyway tests pass. |
| Add owner-filtered Spring Data queries/projections for history, totals, and category summaries | ⚠️ | History uses owner-filtered JPQL. Dashboard aggregation fetches owner-filtered rows and aggregates in Java, not database-side `SUM/COUNT/GROUP BY`. |
| Keep savings/goals out | ✅ | DTOs and controller tests exclude savings/goal fields. |

## TDD Compliance

| Check | Result | Details |
|---|---:|---|
| TDD Evidence reported | ✅ | OpenSpec `apply-progress.md` has `## TDD Cycle Evidence`; Engram apply-progress has merged TDD evidence. |
| All tasks have tests | ✅ | Related unit, controller, integration, migration, and contract tests exist and passed. |
| RED confirmed (tests exist) | ✅ | Listed test files exist: `TransactionServiceTest`, `TransactionControllerTest`, `DashboardControllerTest`, `DashboardServiceTest`, `SecurityEndpointIntegrationTest`, `FlywayMigrationIT`, `TransactionToolContractTest`. |
| GREEN confirmed (tests pass) | ✅ | `./gradlew test --rerun-tasks` passed with 67 tests, 0 failures, 0 errors, 5 skipped. |
| Triangulation adequate | ✅ | History totals have non-empty, category-filter, empty, and two-user isolation cases; dashboard has populated, empty, owner-isolated, and no-savings cases. |
| Safety net for modified files | ✅ | Apply progress records focused red/green commands plus full suite pass. |

**TDD Compliance**: 6/6 checks passed.

## Test Layer Distribution

| Layer | Tests | Files | Tools |
|---|---:|---:|---|
| Unit / contract | 12 | 3 | JUnit 5 + AssertJ |
| Web/controller | 15 | 2 | MockMvc standalone + Mockito |
| Integration / migration | 11 | 2 | SpringBootTest/Testcontainers + Flyway |
| **Total related** | **38** | **7** | |

## Changed File Coverage

Coverage analysis skipped — no coverage tool is configured for this project.

## Assertion Quality

**Assertion quality**: ✅ All reviewed changed tests assert real behavior. No tautologies, ghost loops, assertion-without-production-code checks, or smoke-only tests were found.

## Quality Metrics

- Linter: ➖ Not available.
- Type checker: ✅ Gradle Java compilation passed.
- Coverage: ➖ Not available.

## Issues

### CRITICAL

None.

### WARNING

1. Dashboard aggregate implementation still deviates from the design preference by aggregating in memory after fetching owner-filtered rows rather than using database-side aggregate query/projection. This does not break current spec behavior, but it is less scalable.

### SUGGESTION

1. If data volume grows, replace `TransactionEntityRepository.aggregateByOwnerAndPeriod` with database-side `SUM/COUNT/GROUP BY` projections.
2. Consider updating the Engram tasks artifact to mirror the completed OpenSpec checklist, so future memory-only verification does not read the original unchecked planning baseline.

## Next Recommended

Proceed to `sdd-archive` if the maintainer accepts the remaining design warning.
