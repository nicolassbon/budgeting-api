# Tasks: Persist Weekly Budget

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 320-480 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr-default |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Persist weekly budget end-to-end | PR 1 | One MVP slice; keep migration, wiring, and targeted tests together |

## Phase 1: Schema + RED Tests

- [x] 1.1 Create `src/main/resources/db/migration/V5__add_weekly_budget_amount.sql` adding nullable `app_user.weekly_budget_amount NUMERIC(19,2)`.
- [x] 1.2 Update `src/test/java/dio/budgeting/infraestructure/persistence/FlywayMigrationIT.java` to expect migration `5` and the new `app_user.weekly_budget_amount` column.
- [x] 1.3 Create `src/test/java/dio/budgeting/application/auth/AuthServiceTest.java` with failing cases for read/update lookup, null clearing, and persisted save semantics.
- [x] 1.4 Create `src/test/java/dio/budgeting/infraestructure/http/WeeklyBudgetControllerIT.java` with failing scenarios for null GET, PUT round-trip, clear-to-null, anonymous `401`, and invalid `400` unchanged state.

## Phase 2: Domain + Persistence GREEN

- [x] 2.1 Modify `src/main/java/dio/budgeting/domain/user/User.java` to add `BigDecimal weeklyBudgetAmount` and keep `AuthService.register(...)` defaulting it to `null`.
- [x] 2.2 Modify `src/main/java/dio/budgeting/infraestructure/persistence/entity/UserEntity.java` to map `weekly_budget_amount` and round-trip it in `from(...)` / `toDomain()`.
- [x] 2.3 Modify `src/main/java/dio/budgeting/application/auth/AuthService.java` to add `currentWeeklyBudget(...)`, `updateWeeklyBudget(...)`, and a shared normalized `requireUser(...)` lookup.

## Phase 3: Authenticated HTTP Contract

- [x] 3.1 Extend `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` with `WeeklyBudget` / `WeeklyBudgetRequest` records using `@Valid` and `@PositiveOrZero`.
- [x] 3.2 Add `GET /auth/me/weekly-budget` and `PUT /auth/me/weekly-budget` to `AuthController`, echoing `{ "amount" }` and preserving existing `/auth/me` behavior.
- [x] 3.3 Verify the write path still relies on existing session auth + CSRF contract; do not change `SecurityConfig` unless tests prove a gap.

## Phase 4: GREEN Verification + Cleanup

- [x] 4.1 Make `AuthServiceTest`, `WeeklyBudgetControllerIT`, and `FlywayMigrationIT` pass without weakening validation or anonymous-access rules.
- [x] 4.2 Run targeted verification with `./gradlew test --tests "dio.budgeting.application.auth.AuthServiceTest"`, `./gradlew test --tests "dio.budgeting.infraestructure.http.WeeklyBudgetControllerIT"`, and `./gradlew test --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"`.
- [x] 4.3 Refactor only for clarity after GREEN: imports, helper extraction, and DTO/service naming that preserves the MVP contract.
