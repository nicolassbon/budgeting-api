# Verification Report: persist-weekly-budget

## Change

- Project: `budgeting-api`
- Change: `persist-weekly-budget`
- Mode: `openspec`
- Strict TDD: active
- Verification date: 2026-07-01

## Artifact Inputs

| Artifact | Status | Evidence |
|----------|--------|----------|
| Proposal | ✅ Read | `openspec/changes/persist-weekly-budget/proposal.md` |
| Spec | ✅ Read | `openspec/changes/persist-weekly-budget/specs/weekly-budget/spec.md` |
| Design | ✅ Read | `openspec/changes/persist-weekly-budget/design.md` |
| Tasks | ✅ Read | `openspec/changes/persist-weekly-budget/tasks.md` |
| Apply progress | ⚠️ Recovered | No `apply-progress.md` exists under the active OpenSpec change directory; TDD evidence was recovered from Engram observation `#3280` for the same change. |

## Completeness

| Dimension | Result | Details |
|-----------|--------|---------|
| Task completion | ✅ Complete | Tasks 1.1 through 4.3 are checked in `tasks.md`. |
| Schema implementation | ✅ Complete | `V5__add_weekly_budget_amount.sql` adds nullable `app_user.weekly_budget_amount NUMERIC(19, 2)`. |
| Domain/persistence mapping | ✅ Complete | `User` and `UserEntity` carry `BigDecimal weeklyBudgetAmount`; entity maps `weekly_budget_amount`. |
| Application service | ✅ Complete | `AuthService.currentWeeklyBudget(...)` and `updateWeeklyBudget(...)` use normalized current-user lookup. |
| HTTP contract | ✅ Complete | `AuthController` exposes `GET` and `PUT /auth/me/weekly-budget` with `{ "amount" }` DTOs and validation. |

## Build / Test Evidence

| Command | Result | Evidence |
|---------|--------|----------|
| `./gradlew test --rerun-tasks --tests "dio.budgeting.application.auth.AuthServiceTest" --tests "dio.budgeting.infraestructure.http.WeeklyBudgetControllerIT" --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"` | ✅ Passed | 54s, 7 Gradle tasks executed. Targeted runtime evidence for service, HTTP, and migration slice. |
| `./gradlew test --rerun-tasks` | ✅ Passed | 2m 30s, 115 tests total, 110 passed, 5 skipped OpenAI-gated integration tests, 0 failures, 0 errors. |

Targeted result files after full test run:

| Test class | Tests | Failures | Errors | Skipped |
|------------|-------|----------|--------|---------|
| `AuthServiceTest` | 5 | 0 | 0 | 0 |
| `WeeklyBudgetControllerIT` | 6 | 0 | 0 | 0 |
| `FlywayMigrationIT` | 3 | 0 | 0 | 0 |

Coverage analysis skipped — no coverage tool is configured for this project.

Quality metrics skipped — no lint/type-check task is configured for this project. Java compilation is covered by Gradle `test`.

## Spec Compliance Matrix

| Requirement / Scenario | Runtime Evidence | Status |
|------------------------|------------------|--------|
| Missing budget returns null | `WeeklyBudgetControllerIT.shouldReturnNullWhenWeeklyBudgetIsMissing` | ✅ Compliant |
| Persisted budget round-trips through PUT and GET | `WeeklyBudgetControllerIT.shouldPersistAndReadWeeklyBudgetAmount` | ✅ Compliant |
| Clearing the budget persists null | `WeeklyBudgetControllerIT.shouldClearWeeklyBudgetToNull` | ✅ Compliant |
| Reject anonymous read access | `WeeklyBudgetControllerIT.shouldRejectAnonymousWeeklyBudgetRequests` | ✅ Compliant |
| Reject anonymous write access | `WeeklyBudgetControllerIT.shouldRejectAnonymousWeeklyBudgetRequests` | ✅ Compliant |
| Reject non-numeric amount values and preserve stored value | `WeeklyBudgetControllerIT.shouldRejectInvalidWeeklyBudgetPayloadWithoutChangingStoredAmount` | ✅ Compliant |

## Correctness

| Area | Result | Evidence |
|------|--------|----------|
| Current-user isolation | ✅ Pass | Service lookup derives from authenticated principal email; integration tests create/log in unique users per scenario. |
| Null unset/clear semantics | ✅ Pass | Both service unit tests and HTTP integration tests assert `null` read and clear behavior. |
| Invalid payload no partial persistence | ✅ Pass | HTTP integration test verifies non-numeric payload returns `400` and later `GET` returns the previous value. |
| Negative amount handling | ✅ Pass | Design chose `@PositiveOrZero`; integration test verifies negative values return `400` and preserve previous value. |
| Migration validation | ✅ Pass | `FlywayMigrationIT` verifies migrations `1..5` and exact `app_user.weekly_budget_amount` schema. |

## Design Coherence

| Decision | Implementation | Status |
|----------|----------------|--------|
| Add nullable column on `app_user` | `V5__add_weekly_budget_amount.sql` adds nullable `weekly_budget_amount`. | ✅ Coherent |
| Use `BigDecimal` / `NUMERIC(19,2)` | Domain, entity, DTOs, and migration use `BigDecimal` / numeric precision-scale. | ✅ Coherent |
| Extend `AuthController` | Endpoints are nested under existing `/auth/me` controller. | ✅ Coherent |
| `PUT` returns `{ "amount" }` | Controller echoes persisted `WeeklyBudget`. | ✅ Coherent |
| Preserve existing security / CSRF | No `SecurityConfig` change; `PUT` integration tests use existing CSRF support. | ✅ Coherent |

## TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | ⚠️ | TDD table was not found in OpenSpec because `apply-progress.md` is missing from the change directory; evidence was recovered from Engram `#3280`. |
| All tasks have tests | ✅ | Task rows map to `FlywayMigrationIT`, `AuthServiceTest`, and `WeeklyBudgetControllerIT`. |
| RED confirmed (tests exist) | ✅ | All reported test files exist in the codebase. |
| GREEN confirmed (tests pass) | ✅ | Targeted and full Gradle test runs passed with `--rerun-tasks`. |
| Triangulation adequate | ✅ | 5 service tests + 6 weekly budget HTTP integration scenarios + migration schema assertions cover the spec. |
| Safety Net for modified files | ✅ | Existing baseline auth/migration tests plus targeted new tests passed at runtime. |

**TDD Compliance**: 5/6 checks passed, 1 process warning for missing OpenSpec apply-progress persistence.

## Test Layer Distribution

| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | 5 | 1 | JUnit 5 + AssertJ |
| Integration | 9 | 2 | Spring Boot Test, MockMvc, Testcontainers PostgreSQL |
| E2E | 0 | 0 | Not configured |
| **Total targeted** | **14** | **3** | |

## Assertion Quality

**Assertion quality**: ✅ All reviewed assertions verify real behavior. No tautologies, ghost loops, smoke-only assertions, or type-only assertions were found in `AuthServiceTest`, `WeeklyBudgetControllerIT`, or the updated `FlywayMigrationIT` assertions.

## Issues

### CRITICAL

- None.

### WARNING

- `openspec/changes/persist-weekly-budget/apply-progress.md` is missing even though the active artifact store is `openspec`. Verification recovered the strict TDD evidence from Engram observation `#3280`, but the durable OpenSpec handoff artifact should be present for archive/readiness consistency.

### SUGGESTION

- Consider adding an explicit authenticated `PUT /auth/me/weekly-budget` without CSRF regression test if the team wants the weekly budget slice itself to document the existing CSRF rejection path. Current tests prove successful writes with CSRF and anonymous rejection.

## Final Verdict

PASS WITH WARNINGS

The implementation matches the weekly-budget spec and design, all tasks are checked, targeted tests passed at runtime, and the full `./gradlew test --rerun-tasks` suite passed. The only blocker-adjacent concern is process persistence: strict TDD apply-progress evidence is not stored in the OpenSpec change directory.
