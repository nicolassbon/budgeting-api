# Tasks: Dashboard Spending Metrics

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 800–950 |
| 400-line budget risk | High |
| 800-line budget risk | Medium-High |
| Chained PRs recommended | Yes |
| Suggested split | Single PR with `size:exception`, or PR 1 (domain/persistence) → PR 2 (services/DTOs) → PR 3 (controllers/tests) |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High
800-line budget risk: Medium-High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Migration, domain model, persistence entity, repository contracts | PR 1 | Foundation; blocks all other work |
| 2 | Application services, inputs/outputs, timestamp defaulting | PR 2 | Depends on PR 1 |
| 3 | Dashboard/history controllers and full test coverage | PR 3 | Depends on PR 2; base = PR 2 branch |

## Phase 1: Foundation

- [x] 1.1 Create `src/main/resources/db/migration/V4__add_transaction_occurred_at.sql` adding `occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`.
- [x] 1.2 Add `Instant occurredAt` to `domain/Transaction.java` and update constructors/factory methods preserving existing call sites.
- [x] 1.3 Map `occurred_at` in `infraestructure/persistence/entity/TransactionEntity.java` and update `from/toDomain`.
- [x] 1.4 Add owner-scoped query contracts to `domain/TransactionRepository.java`: history, totals, category aggregation.

## Phase 2: Application Layer

- [x] 2.1 Add optional `occurredAt` to `application/input/PersistTransactionInput.java` and default to `Instant.now()` in `TransactionService.create`.
- [x] 2.2 Add `occurredAt` to `application/output/TransactionOutput.java` and `infraestructure/http/response/TransactionResponse.java`.
- [x] 2.3 Add history filters record and `findHistory` use case to `application/TransactionService.java`.
- [x] 2.4 Create `application/DashboardService.java` building `DashboardSummaryResponse` with period bounds, total, count, and top categories.

## Phase 3: HTTP Layer

- [x] 3.1 Create `infraestructure/http/DashboardController.java` exposing `GET /dashboard/spending`.
- [x] 3.2 Add `GET /transactions` history endpoint to `infraestructure/http/TransactionController.java` with `from`, `to`, and `category` filters.
- [x] 3.3 Update `TransactionController.createTransaction` to accept optional date in `TransactionRequest`.

## Phase 4: Persistence Implementation

- [x] 4.1 Implement `TransactionRepository` methods in `infraestructure/persistence/repository/JpaTransactionRepository.java`.
- [x] 4.2 Add Spring Data query methods/projections to `TransactionEntityRepository.java` for owner-filtered history and aggregation.

## Phase 5: Tests (Strict TDD)

- [x] 5.1 Write failing `TransactionServiceTest` for timestamp defaulting and history owner filtering, then make it pass.
- [x] 5.2 Write failing `DashboardServiceTest` for period totals and category aggregation, then make it pass.
- [x] 5.3 Write failing controller tests for `GET /dashboard/spending` and `GET /transactions`, then make them pass.
- [x] 5.4 Update `FlywayMigrationIT` to assert V4 applies and `occurred_at` column exists.
- [x] 5.5 Add repository integration test proving owner isolation across users and period/category filters.
- [x] 5.6 Run regression tests for `POST /transactions`, `GET /transactions/{category}`, and `POST /transactions/ai`.

## Phase 6: Verification

- [x] 6.1 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"`.
- [x] 6.2 Run `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"`.
- [x] 6.3 Run full `./gradlew test`.
