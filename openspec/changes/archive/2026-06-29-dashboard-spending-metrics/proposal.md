# Proposal: Dashboard Spending Metrics

## Intent

Make existing frontend views more useful: Inicio shows current-user spending summary cards, and Historial supports detailed owner-scoped exploration. Today transactions are owner-scoped but lack dates, list-all history, filters, and aggregates, so period metrics cannot be computed correctly.

## Proposal question round

Automatic-mode assumptions for user review: period cards use persisted transaction date, totals are expense-only, savings stay out, and no separate Analytics view is introduced.

## Scope

### In Scope
- Add transaction date/timestamp persistence and API response support needed for period metrics.
- Add an authenticated, owner-scoped dashboard summary endpoint/service for Inicio cards.
- Add an authenticated, owner-scoped transaction history/list endpoint with optional period/category filters and totals context for Historial.
- Preserve existing `POST /transactions`, `GET /transactions/{category}`, and AI transaction behavior.

### Out of Scope
- Savings/goals metrics or `AHORRO` category semantics.
- Separate Analytics view/backend surface.
- Edit/delete transaction workflows.

## Capabilities

### New Capabilities
- `dashboard-spending-metrics`: Owner-scoped spending summary for Inicio, including period totals and category context.

### Modified Capabilities
- `transaction-api`: Add transaction date support and owner-scoped history/list behavior while preserving current create/list-by-category contracts.

## Approach

Use the exploration recommendation: introduce a narrow dashboard application service/controller for summary metrics, keep transaction CRUD compatible, and extend transaction persistence through Flyway because Hibernate validates schema. Place business coordination in application services, HTTP DTOs/controllers in `infraestructure/http`, and queries in persistence repositories.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/Transaction.java` | Modified | Add date/timestamp to transaction model. |
| `infraestructure/persistence/entity/TransactionEntity.java` | Modified | Persist and map transaction date/timestamp. |
| `src/main/resources/db/migration/` | New | Add Flyway migration for date column/backfill default. |
| `application/` | New/Modified | Add dashboard service and history use case. |
| `infraestructure/http/` | New/Modified | Add dashboard/history endpoints and DTOs. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Date backfill changes existing data semantics | Med | Use explicit migration default and document behavior in specs/design. |
| Cross-user data leakage in aggregate queries | Med | Require every query to filter by authenticated owner. |
| Response shape overfits future analytics | Low | Keep MVP cards/history narrow and defer savings/Analytics. |

## Rollback Plan

Revert new dashboard/history endpoints, service/repository queries, DTOs, and Flyway migration before release. If already applied locally, reset disposable dev DB volumes; do not patch Flyway history manually.

## Dependencies

- Existing session auth and owner-scoped transaction ownership.
- PostgreSQL/Flyway migration must run before Hibernate validation.

## Success Criteria

- [ ] Inicio can request authenticated current-user spending summary cards.
- [ ] Historial can request owner-scoped transaction history with useful filter/total context.
- [ ] Existing transaction create/list-by-category and AI endpoints remain compatible.
