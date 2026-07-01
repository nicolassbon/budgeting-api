# Design: Persist Weekly Budget

## Technical Approach

Add a single nullable `weekly_budget_amount NUMERIC(19,2)` column to `app_user` via `V5`, map it through `User` and `UserEntity` (as a `BigDecimal`), and extend the existing `AuthController` + `AuthService` authenticated flow with two thin endpoints: `GET/PUT /auth/me/weekly-budget`. Round-trip JSON shape is `{ "amount": number | null }` per the proposal/spec. `null` is the "unset" sentinel rather than a `404`. No new table, no preferences framework; the column is additive and nullable, so the migration is backward compatible.

## Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Column on `app_user` vs separate `user_preferences` table | Column is fast, scoped, and migration-light; a table is more extensible but premature for one value. | **Column on `app_user`** — defer a preferences table until a second setting justifies it. |
| `BigDecimal` vs `Long` cents | `BigDecimal` matches the Java coding-standards Money idiom and the spec's `number` (decimals); cents avoids FP but diverges from spec text. | **`BigDecimal` `NUMERIC(19,2)`** — direct JSON round-trip, canonical for currency. |
| Extend `AuthController` vs new `WeeklyBudgetController` | Extending is the smallest patch and reuses `/auth` base + injection; a new controller isolates concerns if pref grows. | **Extend `AuthController`** — MVP minimal; extract later if `/me/*` grows. |
| Return body on PUT vs `204 No Content` | Spec requires round-trip JSON via subsequent GET; echoing on PUT removes a round-trip. | **`200 OK` with `{ "amount" }`** on PUT (echo persisted value). |
| Validation: reject negatives | Spec only mandates rejecting non-numeric; rejecting negatives is sane for a budget. | **`@PositiveOrZero` (null allowed)** on request DTO — no partial persistence on invalid input. |

## Data Flow

    Client ──PUT/GET──▶ AuthController (/auth/me/weekly-budget)
                              │
                              ▼
                         AuthService  ──normalize(email)──▶ UserRepository.findByEmail
                              │                                        │
                              │                                        ▼
                              │                                    User (with weeklyBudgetAmount)
                              │                                        │
                              ▼                                        ▼
                      WeeklyBudget DTO ◀──toDomain──── UserEntity ◀── JPA / app_user.weekly_budget_amount

PUT flow reuses `UserRepository.save` (Spring Data `CrudRepository.save` merges for non-null id since `from(user)` carries the existing id).

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/resources/db/migration/V5__add_weekly_budget_amount.sql` | Create | `ALTER TABLE app_user ADD COLUMN weekly_budget_amount NUMERIC(19,2);` (nullable). |
| `src/main/java/dio/budgeting/domain/user/User.java` | Modify | Append `BigDecimal weeklyBudgetAmount` as last record component. |
| `src/main/java/dio/budgeting/infraestructure/persistence/entity/UserEntity.java` | Modify | Add `@Column(name="weekly_budget_amount", precision=19, scale=2) BigDecimal weeklyBudgetAmount`; update `from` / `toDomain` and `@AllArgsConstructor` arg order (id, email, password, role, weeklyBudgetAmount). |
| `src/main/java/dio/budgeting/application/auth/AuthService.java` | Modify | Add `currentWeeklyBudget(email)` and `updateWeeklyBudget(email, amount)`; private `requireUser(email)`; pass `null` for the new component in `register`'s `new User(...)`. |
| `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` | Modify | Add `GET /me/weekly-budget` and `PUT /me/weekly-budget`; nested records `WeeklyBudget(BigDecimal amount)` and `WeeklyBudgetRequest(@PositiveOrZero BigDecimal amount)` with `@Valid`. |
| `src/test/java/dio/budgeting/infraestructure/persistence/FlywayMigrationIT.java` | Modify | Expect versions `"1".."5"` (3 assertions); append `new ColumnState("weekly_budget_amount","numeric","YES","NO")` to the `app_user` column assertion. |
| New `WeeklyBudgetControllerIT` + `AuthServiceTest` | Create | Integration round-trip (incl. CSRF token on PUT, 401 for anonymous) and unit coverage for the service. |

## Interfaces / Contracts

```java
// User.java
public record User(Long id, String email, String password, UserRole role,
                   java.math.BigDecimal weeklyBudgetAmount) {}

// AuthController nested records
public record WeeklyBudget(java.math.BigDecimal amount) {}
public record WeeklyBudgetRequest(
    @jakarta.validation.constraints.PositiveOrZero java.math.BigDecimal amount) {}
```

CSRF: PUT is state-changing and the existing `SecurityConfig` enables `CookieCsrfTokenRepository`. The frontend must send the CSRF token on `PUT` (same contract as any state-changing request under `/auth`); no `SecurityConfig` change required.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Integration | Migrations apply V5; `app_user` schema validates | Update `FlywayMigrationIT` versions + columns; deterministic via Testcontainers Postgres. |
| Integration | `GET`/`PUT` round-trip, `null` clear, anonymous `401`, invalid payload `400` no partial write | New `WeeklyBudgetControllerIT` (`@SpringBootTest` + MockMvc + session auth + CSRF). |
| Unit | `AuthService.currentWeeklyBudget` / `updateWeeklyBudget` lookup + save semantics | New `AuthServiceTest` with mocked `UserRepository`. |

## Migration / Rollout

Additive nullable column → no backfill, no data downtime. Hibernate `ddl-auto=validate` requires the entity column and the migration to ship together; that is enforced by `FlywayMigrationIT`. Rollback (if deployed): revert code and add a follow-up migration `DROP COLUMN weekly_budget_amount`.

## Open Questions

- [ ] Should `PUT` reject negative amounts explicitly? Design assumes yes (`@PositiveOrZero`); spec only mandates non-numeric rejection — confirm in spec.
- [ ] Should `/auth/me` (`AuthenticatedUser`) eventually include `weeklyBudgetAmount`? Out of scope here; kept stable.