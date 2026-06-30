# Design: Dashboard Spending Metrics

## Technical Approach

Add a narrow spending-dashboard slice on top of the existing layered transaction model. `TransactionService` keeps create/category behavior compatible, while new dashboard/history read use cases expose owner-scoped metrics for Inicio and Historial. Persistence changes go through Flyway because Hibernate validates the schema before startup completes.

## Architecture Decisions

| Topic | Options / Tradeoff | Decision |
|------|---------------------|----------|
| Dashboard boundary | Reusing `TransactionService` is smaller but mixes write/tool-calling concerns with dashboard reads. | Create `DashboardService` and `DashboardController`; keep transaction history in `TransactionService` because it is transaction-list behavior. |
| Repository queries | In-memory aggregation is simple but wrong at scale and easier to leak owners. | Add owner-filtered Spring Data queries/projections for history, totals, and category summaries. |
| Timestamp type | `LocalDate` is easy for cards but loses actual event time; `Instant` is deterministic and maps cleanly to PostgreSQL. | Add `occurredAt: Instant`, persisted as `TIMESTAMPTZ`, defaulted in application on create. |
| Backfill | Nulls avoid invented dates but complicate every query. | Migration adds `occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`; existing rows belong to migration time by explicit MVP convention. |
| Savings | Adding savings now would blur expense semantics. | Keep all metrics expense-only; reserve DTO extension fields but no `AHORRO` behavior. |

## Data Flow

```text
SecurityContext -> AuthenticatedUserProvider -> Service
                                          |-> TransactionRepository owner-scoped queries
HTTP DTO <- Controller <- Output DTO <----|
```

All dashboard and history reads require `requireCurrentUserId()` and pass `ownerId` into repository methods. No aggregate query may run without owner filtering.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `domain/Transaction.java` | Modify | Add `Instant occurredAt`; preserve constructors through overload/defaults. |
| `domain/TransactionRepository.java` | Modify | Add owner-scoped history and aggregation contracts. |
| `application/DashboardService.java` | Create | Builds Inicio summary cards from repository projections. |
| `application/TransactionService.java` | Modify | Default create timestamp and expose history use case. |
| `application/input/*` | Create/Modify | Add optional filters and timestamp-aware create input. |
| `application/output/*` | Create/Modify | Add summary/history outputs and timestamp in transaction output. |
| `infraestructure/http/DashboardController.java` | Create | `GET /dashboard/spending` per spec. |
| `infraestructure/http/TransactionController.java` | Modify | Add `GET /transactions` history with query filters; preserve existing routes. |
| `infraestructure/persistence/entity/TransactionEntity.java` | Modify | Map `occurred_at`. |
| `infraestructure/persistence/repository/*` | Modify | Add `@Query`/derived methods with `ownerId`, date range, category. |
| `src/main/resources/db/migration/V4__add_transaction_occurred_at.sql` | Create | Add/backfill timestamp column. |
| `src/test/java/...` | Modify/Create | Unit, controller, repository/migration coverage. |

## Interfaces / Contracts

```java
record DashboardSummaryResponse(
    PeriodResponse period,
    long totalAmountCents,
    double totalAmount,
    long transactionCount,
    List<CategoryTotalResponse> topCategories
) {}

record TransactionHistoryResponse(
    List<TransactionResponse> items,
    long totalAmountCents,
    double totalAmount,
    long transactionCount
) {}
```

Filters: `from` and `to` are ISO `LocalDate` query params; `category` is optional. Boundaries are inclusive by user-facing date: `from` maps to start-of-day UTC, `to` maps to next-day-exclusive UTC. Amounts remain stored as cents (`long`); API may include `double` only as display compatibility, never for aggregation.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | timestamp defaulting, owner propagation, amount totals | JUnit tests with fake repositories. |
| Web | response shapes, filters, route compatibility | Controller tests for dashboard and `GET /transactions`. |
| Integration | Flyway column/backfill, Hibernate validation, repository owner isolation | `FlywayMigrationIT`, repository/Testcontainers or focused Spring tests. |
| Regression | AI/create/category endpoints unchanged | Existing transaction and assistant tests. |

## Migration / Rollout

No phased rollout required. Apply Flyway migration before code expecting `occurred_at`; local divergent history should be reset with Docker volumes, not manual Flyway edits.

## Open Questions

- [ ] Should the frontend eventually send a user timezone? MVP uses UTC boundaries until that contract exists.
