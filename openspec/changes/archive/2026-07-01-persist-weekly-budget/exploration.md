# Exploration: persist-weekly-budget

## Current State

The backend has a fully working `app_user` table (id, email, password, role) created in `V2__auth_and_transaction_ownership.sql`. `User` is a Java record (`dio.budgeting.domain.user.User`), `UserEntity` is the JPA mapping with `from(User)`/`toDomain()` pair, and `UserRepository` is a tiny domain interface (`findByEmail`, `save`) implemented by `JpaUserRepository` via `UserEntityRepository extends CrudRepository<UserEntity, Long>`.

Authentication is already wired end-to-end:

- `SecurityConfig` requires authentication for any request that is not `/auth/register` or `/auth/login` (`dio.budgeting.config.SecurityConfig` lines 39-42). A new authenticated endpoint gets session-cookie protection for free.
- `AuthController.me(...)` (`/auth/me`) and `AuthService.currentUser(email)` already return the current user.
- `SecurityContextAuthenticatedUserProvider.requireCurrentUserId()` (`dio.budgeting.application.security.AuthenticatedUserProvider`) is the canonical way to resolve the current user id inside any service.
- JPA runs with `spring.jpa.hibernate.ddl-auto=validate` (per project AGENTS.md), and `dio.budgeting.config.FlywayConfig` wires Flyway to run before Hibernate. Any new persistent field requires a Flyway migration in the same change, or startup will fail.

A grep for `weekly_budget`, `weeklyBudget`, `preferences`, and `user_settings` across the whole repo returns **zero matches**. The frontend currently stores the weekly budget only in `localStorage` (per the change context). There is no existing preferences/settings concept to extend or conflict with.

The existing migrations are V1–V4. The next free slot is **V5**.

## Affected Areas

For Approach 1 (recommended — nullable column on `app_user`):

- `src/main/resources/db/migration/V5__add_weekly_budget_amount.sql` — new migration adding a nullable `NUMERIC(19,2)` column to `app_user`. One `ALTER TABLE`.
- `src/main/java/dio/budgeting/domain/user/User.java` — add a nullable `BigDecimal weeklyBudgetAmount` component to the record.
- `src/main/java/dio/budgeting/infraestructure/persistence/entity/UserEntity.java` — add the matching `@Column(precision = 19, scale = 2) private BigDecimal weeklyBudgetAmount;` plus update `from(User)` and `toDomain()` mapping.
- `src/main/java/dio/budgeting/domain/user/UserRepository.java` — add `Optional<User> findById(Long id)` so the service can load the current user by id (already has `save`).
- `src/main/java/dio/budgeting/infraestructure/persistence/repository/JpaUserRepository.java` — implement `findById` against `UserEntityRepository.findById` (inherited from `CrudRepository`).
- `src/main/java/dio/budgeting/application/auth/AuthService.java` — add `getWeeklyBudget(Long userId)` and `updateWeeklyBudget(Long userId, BigDecimal amount)` (or a single `WeeklyBudgetPreference` value type). The methods resolve the user by id, read or update the field, and `save`.
- New DTOs under `src/main/java/dio/budgeting/infraestructure/http/response/` and `request/` — e.g. `WeeklyBudgetResponse(BigDecimal amount)` and `UpdateWeeklyBudgetRequest(@NotNull @DecimalMin("0.01") BigDecimal amount)` (or allow `null` to clear; TBD in spec).
- `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` — add `GET /auth/me/weekly-budget` and `PUT /auth/me/weekly-budget`, **or** create a new `UserPreferencesController` (cleaner, but the field is small enough that extending `AuthController` is the smallest MVP slice).
- `src/test/java/dio/budgeting/infraestructure/persistence/FlywayMigrationIT.java` — **must** be updated. Lines 53-58 assert the exact `app_user` columns (`id, email, password, role`); adding `weekly_budget_amount` will break that test unless the new column is added to the expected list in the same change.
- Optional: integration tests for the new service and endpoint under `src/test/java/dio/budgeting/application/auth/` and `src/test/java/dio/budgeting/infraestructure/http/` (recommended given `strict_tdd: true`).

For Approach 2 (separate preferences table — listed for completeness, **not recommended**):

- `src/main/resources/db/migration/V5__create_user_preferences.sql` — new table with FK to `app_user(id)`, plus a unique index on `user_id`.
- New domain record `UserPreferences` and domain interface `UserPreferencesRepository` (`findByUserId`, `save`).
- New JPA entity `UserPreferencesEntity`, new `UserPreferencesEntityRepository extends CrudRepository`, new `JpaUserPreferencesRepository` adapter.
- New application service `UserPreferencesService`, new controller `UserPreferencesController` (or extend `AuthController`), new request/response DTOs.
- `FlywayMigrationIT` will need the new `user_preferences` table asserted too.

## Approaches

### 1. Nullable `weekly_budget_amount` column on `app_user`

- Pros:
  - **Smallest possible slice**: 1 Flyway migration, 1 column, 1 entity field, 1 domain record component, 1 new repo method (`findById`), 1 service method pair, 1 controller pair, 1 request/response record pair.
  - The `currentUser`/`/auth/me` flow already loads the user record; the weekly budget is just an extra field on that same record — no extra read, no join, no second table.
  - `null` cleanly models "user has not configured a weekly budget yet" — matches the current localStorage behavior (budget is absent until set).
  - No backfill required (nullable, no default needed), no FK to maintain, no risk of orphan rows.
  - Auto-protected by the existing `SecurityConfig.anyRequest().authenticated()` rule — no security wiring to add.
  - Reuses `AuthenticatedUserProvider` and the `from(User)/toDomain()` mapping pattern that already exists.
  - The 2nd setting (if/when it arrives) can be promoted to a `user_preferences` table via a follow-up Flyway migration that copies values and drops the column. Cost of that future migration is low because the column is isolated.
- Cons:
  - Couples user identity with a single setting. For a one-field MVP this is acceptable, but a 2nd/3rd setting will make the case for a separate table stronger.
  - Mildly violates "one entity per concern" — acceptable for MVP, call it out in the proposal.
- Effort: **Low** — ~7 small files, 1 SQL migration (~3 lines), ~80–120 LOC of Java, plus the `FlywayMigrationIT` update.

### 2. Separate `user_preferences` table with 1:1 to `app_user`

- Pros:
  - Cleaner separation: user identity vs. user settings — the natural home once multiple settings exist.
  - Avoids touching `app_user` itself, which keeps the `FlywayMigrationIT` `app_user`-column assertion stable.
  - Easier to add new settings without further `app_user` migrations.
- Cons:
  - Larger surface: new table, new entity, new `CrudRepository`, new adapter, new domain repo, new service, new DTOs, new controller (or new endpoints in `AuthController`).
  - Every read of the weekly budget requires a second query (or an eager join) — wasted work for a single nullable column.
  - Needs FK + a backfill or NOT NULL plan to avoid orphan rows. A 1:1 with the user has to be created at registration, which couples the auth service to the preferences service.
  - With `ddl-auto=validate`, every new field added to `UserPreferencesEntity` must be matched by a Flyway column add — same discipline as Approach 1 but on a new table, not less.
  - Premature abstraction for a single field. "We'll probably need more settings" is not a paid bet in MVP.
- Effort: **Medium** — ~10+ files, 1 SQL migration with FK + unique index (~8 lines), ~180–250 LOC of Java, plus `FlywayMigrationIT` update for the new table.

## Recommendation

**Approach 1**: add a nullable `weekly_budget_amount NUMERIC(19,2)` column to `app_user` via a new `V5__add_weekly_budget_amount.sql` migration, expose `GET /auth/me/weekly-budget` and `PUT /auth/me/weekly-budget` that round-trip a `{ "amount": number | null }` JSON body, and reuse the existing `SecurityContextAuthenticatedUserProvider` to resolve the current user id.

Why:

- It is the **fastest MVP-safe slice** — one Flyway migration, one entity field, one read, one write, and the existing authenticated session check covers the endpoint for free.
- The current user record is already loaded by `AuthService.currentUser(email)`; the weekly budget is a natural column on that record and reads cost nothing extra.
- The JPA `ddl-auto=validate` discipline is preserved (migration + entity field land together; `FlywayMigrationIT` is updated in the same change).
- The future migration cost to a dedicated `user_preferences` table is small when (and only when) a 2nd setting arrives — the data is already in one column that can be copied and dropped.

## Risks

- **`FlywayMigrationIT` breaks** — lines 53-58 assert the exact `app_user` columns. The change MUST add the new `weekly_budget_amount` column to the expected list (`new ColumnState("weekly_budget_amount", "numeric", "YES", "NO")`) in the same change, or `apply` will fail CI.
- **`BigDecimal` vs `double` consistency** — transaction amounts use `double` in `TransactionResponse`, but for a stored money value the safer choice is `BigDecimal`. Recommend `BigDecimal` for the new field and JSON-serialize as a number; keep the transport and storage types aligned. (If the frontend expects a plain number, JSON binding via Jackson handles `BigDecimal` as a number by default.)
- **The 2nd preference setting** will force a refactor to a dedicated preferences table. Acceptable for MVP, but call it out explicitly in the proposal so the team knows the path.
- **CSRF** — `SecurityConfig` enables `CookieCsrfTokenRepository` with `httpOnly=false`. The frontend must send the CSRF token on the `PUT`. Confirm the frontend already does so for `/auth/me` flows; if not, this is the right time to align.
- **Future `ddl-auto=validate` strictness** — if the entity field is added without the Flyway column (or vice versa), the app refuses to start. The change must land both atomically.
- **No new security wiring needed** — but the proposal should confirm the endpoint is behind the existing `.anyRequest().authenticated()` rule and not accidentally `permitAll()`'d.

## Ready for Proposal

**Yes.** The orchestrator can move to `sdd-propose` for `persist-weekly-budget`. The proposal should:

- Frame the problem as "weekly budget is currently localStorage-only; the MVP needs cross-device persistence".
- Lock the recommendation (nullable `weekly_budget_amount` on `app_user`, `GET/PUT /auth/me/weekly-budget`).
- Lock the data type (`BigDecimal`, `NUMERIC(19,2)`, JSON shape `{ "amount": number | null }`).
- Lock the migration slot (`V5__add_weekly_budget_amount.sql`) and call out the `FlywayMigrationIT` column-assertion update as part of the change.
- Note the future migration path to `user_preferences` when a 2nd setting shows up, and the 400-line PR review budget — this change is well under it (estimated <120 LOC Java + 1 short SQL migration), so a single PR is safe.
