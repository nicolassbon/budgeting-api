# Dashboard Spending Metrics Exploration

## Current State

The backend already has authenticated sessions, owner-scoped transaction writes/reads, and stable transaction AI endpoints. `AuthService` registers/logs in users, `SecurityContextAuthenticatedUserProvider` resolves the current user id from the session, and `TransactionService` saves and lists transactions only for that owner. Transactions currently support create and list-by-category only.

Transaction persistence is minimal: `TransactionEntity` stores `description`, `amount`, `category`, and `owner_id`. There is no transaction date/timestamp, no list-all endpoint, no edit/delete flow, and no aggregate query/API for totals by period, category, or trend.

## Affected Areas

- `src/main/java/dio/budgeting/application/TransactionService.java` — current transaction use-case entry point; likely home for dashboard aggregation if kept as application service.
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` — current transaction HTTP adapter; could host a dashboard endpoint if metrics stay transaction-adjacent.
- `src/main/java/dio/budgeting/domain/Transaction.java` — domain model currently lacks a date field needed for period metrics.
- `src/main/java/dio/budgeting/infraestructure/persistence/entity/TransactionEntity.java` — persistence model also lacks date/timestamp.
- `src/main/resources/db/migration/V1__Initial_schema.sql` / `V2__auth_and_transaction_ownership.sql` — schema history shows only amount/category/owner_id today.
- `src/main/resources/db/migration/V3__rename_transaction_categories_to_spanish.sql` — confirms category normalization is already a migration concern.

## Approaches

1. **Dashboard application service + dedicated controller** — add a small `DashboardService` (or `SpendingMetricsService`) that aggregates owner-scoped transactions and expose `GET /dashboard/spending` or similar.
   - Pros: clean separation from transaction CRUD, easy to evolve into Inicio cards and later charts.
   - Cons: adds a new surface area and one more service/controller pair.
   - Effort: Medium

2. **Extend transaction endpoints with summary views** — keep metrics under `/transactions`, e.g. `/transactions/summary` and `/transactions/by-period`.
   - Pros: fewer new concepts, close to existing data.
   - Cons: mixes CRUD and analytics concerns; endpoint growth becomes messy fast.
   - Effort: Low/Medium

3. **Return dashboard data from existing list/create flows** — enrich transaction responses with aggregates.
   - Pros: minimal endpoint count.
   - Cons: wrong shape for the problem; creates heavy responses and couples unrelated concerns.
   - Effort: Low

## Recommendation

Use a **dedicated dashboard application service/controller** with a narrow MVP response for Inicio. Keep transaction CRUD unchanged, and add one owner-scoped metrics endpoint that can later expand into more cards.

## Risks

- No transaction date means period metrics are impossible without a schema migration; using insert order would be incorrect.
- `amount` is a `long`, so all aggregates must preserve currency-unit semantics and avoid decimal drift.
- Category totals must stay owner-scoped; any repository query must filter by `owner_id` first.
- The current category enum is expense-oriented and excludes savings (`AHORRO`), so dashboard metrics should not assume savings is already a transaction category.
- If metrics are added too early to transaction responses, the API may become hard to evolve for future savings/goal views.

## Ready for Proposal

Yes — the next step is to define a minimal dashboard MVP spec centered on Inicio cards, plus the required date-field migration.
