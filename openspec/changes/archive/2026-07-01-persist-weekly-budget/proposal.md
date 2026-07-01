# Proposal: Persist Weekly Budget

## Intent

Persist the current user's weekly budget on the backend so it survives browser/device changes. Today the value is frontend-local only; the MVP needs a small authenticated read/write API without creating a broader preferences system.

## Scope

### In Scope
- Add nullable `weekly_budget_amount` directly on `app_user`.
- Expose authenticated `GET /auth/me/weekly-budget` and `PUT /auth/me/weekly-budget`.
- Round-trip JSON as `{ "amount": number | null }`, where `null` means unset/clear.
- Update Flyway migration coverage, including the load-bearing `FlywayMigrationIT` exact `app_user` column assertion.

### Out of Scope
- General preferences/settings table or framework.
- Frontend implementation in this repo.
- Analytics, dashboard, alerting, history, or budget recommendations.

## Capabilities

### New Capabilities
- `weekly-budget`: Persists and exposes the current authenticated user's optional weekly budget amount.

### Modified Capabilities
- None.

## Approach

Use the fastest MVP path: add `weekly_budget_amount NUMERIC(19,2) NULL` via `V5__add_weekly_budget_amount.sql`, map it through `User`/`UserEntity`, and extend the auth application/controller flow for current-user weekly budget read/write. Reuse existing authenticated session security; the frontend must send the CSRF token for `PUT` because Spring Security uses CSRF protection for state-changing requests.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/resources/db/migration/` | New | Add V5 migration for nullable column. |
| `dio.budgeting.domain.user` | Modified | Add weekly budget field/repository lookup support. |
| `dio.budgeting.infraestructure.persistence` | Modified | Map the new `app_user` column. |
| `dio.budgeting.application.auth` | Modified | Read/update current user's weekly budget. |
| `dio.budgeting.infraestructure.http` | Modified | Add minimal GET/PUT DTOs/endpoints. |
| `src/test/java/.../FlywayMigrationIT.java` | Modified | Include `weekly_budget_amount` in exact schema assertions. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Schema validation fails | Medium | Ship Flyway migration, entity mapping, and `FlywayMigrationIT` update together. |
| PUT rejected by CSRF | Medium | Document/test CSRF token expectation for authenticated writes. |
| Premature preference-table need | Low | Defer until a second setting justifies migration. |

## Rollback Plan

Revert the endpoint/service/entity changes and add a follow-up Flyway migration to drop `app_user.weekly_budget_amount` if already deployed. No backfill is required because the column is nullable.

## Dependencies

- Existing session authentication and CSRF configuration.
- PostgreSQL/Flyway migration slot `V5`.

## Success Criteria

- [ ] Authenticated users can GET their current amount or `null`.
- [ ] Authenticated users can PUT a number or `null`, then GET the same value.
- [ ] Unauthenticated requests remain rejected.
- [ ] `FlywayMigrationIT` validates the new `app_user` schema.
