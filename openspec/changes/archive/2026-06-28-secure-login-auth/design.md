# Design: Secure Login Authentication

## Technical Approach

Add Spring Security session auth around the existing layered backend, then push ownership enforcement into application and persistence boundaries. This matches the proposal/specs by keeping `/transactions/**` and `/api/*` paths stable, adding local register/login/logout/me endpoints, and scoping transaction + AI tool flows to the authenticated user.

## Architecture Decisions

| Decision | Options / tradeoff | Choice + rationale |
|---|---|---|
| Auth mechanism | JWT adds token issuance, rotation, revocation, storage, and logout complexity; server sessions fit a single backend and Spring Security defaults. | Use server-side sessions with `JSESSIONID` httpOnly cookie. Faster MVP, easier logout/revocation, and no client token parsing. |
| Security boundary | Controller-only checks are easy to miss in AI tool calls; repository-only checks hide auth intent. | Enforce auth in `SecurityFilterChain`, then resolve current user inside application services and owner-scoped repositories. This protects HTTP and Spring AI tool paths consistently. |
| CSRF for cookie auth | Disabling CSRF is simpler but weak for browser cookie auth. | Keep CSRF enabled with `CookieCsrfTokenRepository`; login/register bootstrap anonymously, authenticated POST/multipart/logout requests send `X-XSRF-TOKEN`. |
| Roles | Join table is extensible but heavier for MVP. | Store a single least-privileged role on `app_user` now (`USER`), leaving room for a later role table only if needed. |

## Data Flow

1. `POST /auth/register` hashes password with BCrypt and saves `app_user(role=USER)`.
2. `POST /auth/login` authenticates by email/password, rotates session, returns `JSESSIONID`; `GET /auth/me` returns current identity and primes CSRF cookie.
3. Protected request enters Spring Security.
4. `TransactionService` / auth service asks `AuthenticatedUserProvider` for the current user id.
5. Repository queries always include owner scope.

```text
Client + cookies ─→ SecurityFilterChain ─→ Controller ─→ Application service
       │                    │                         │            │
       └─ X-CSRF-TOKEN ─────┘                         └──── ownerId lookup
Application service ─→ owner-scoped repository ─→ PostgreSQL
```

`POST /transactions/ai`, `POST /transactions/interpret`, `POST /api/transcribe`, and `POST /api/sinthesize` stay multipart/JSON compatible; they simply require session + CSRF when authenticated.

The current transaction HTTP API has no route that accepts a transaction id or otherwise targets an existing transaction owned by another user. For this change, cross-user isolation is enforced by owner-scoped create/list service and repository boundaries; a future targeted read/update/delete route must add an explicit ownership check that returns `403 Forbidden` when the target owner differs from the authenticated session owner.

## File Changes

| File | Action | Description |
|---|---|---|
| `build.gradle` | Modify | Add Spring Security starter. |
| `src/main/java/dio/budgeting/config/SecurityConfig.java` | Create | Session, CSRF, endpoint rules, 401 handling, password encoder, auth provider. |
| `src/main/java/dio/budgeting/application/security/AuthenticatedUserProvider.java` | Create | Application-facing current-user port. |
| `src/main/java/dio/budgeting/infraestructure/security/SecurityContextAuthenticatedUserProvider.java` | Create | SecurityContext adapter. |
| `src/main/java/dio/budgeting/domain/user/*` | Create | `User`, `UserRole`, `UserRepository`. |
| `src/main/java/dio/budgeting/application/auth/*` | Create | Register/login/current-user use cases and DTOs. |
| `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` | Create | `/auth/register`, `/auth/login`, `/auth/logout`, `/auth/me`. |
| `src/main/java/dio/budgeting/application/TransactionService.java` | Modify | Resolve authenticated owner internally for create/list tool methods. |
| `src/main/java/dio/budgeting/domain/Transaction*.java` | Modify | Add owner reference to the domain model and repository contract. |
| `src/main/java/dio/budgeting/infraestructure/persistence/**/*` | Modify | Map `owner_id`, add user repositories, owner-scoped queries. |
| `src/main/resources/db/migration/V2__auth_and_transaction_ownership.sql` | Create | User table + transaction ownership rollout. |
| `src/test/java/dio/budgeting/**/*` | Modify/Create | Security, auth, ownership, and migration tests. |

## Interfaces / Contracts

```java
public interface AuthenticatedUserProvider {
    Long requireCurrentUserId();
}

public interface UserRepository {
    Optional<User> findByEmail(String email);
    User save(User user);
}
```

`TransactionService.create(PersistTransactionInput)` and `findAllByCategory(Category)` stay tool-visible, but internally call owner-aware persistence.

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Password hashing, duplicate-email rejection, owner-scoped transaction service behavior | JUnit 5 + fakes/mocks. |
| Integration | Register/login/logout/me, 401 on anonymous access, session+CSRF on protected POST/multipart, owner-scoped persistence | `@SpringBootTest` + `@AutoConfigureMockMvc`. |
| Integration | Schema validity and ownership migration | Extend `FlywayMigrationIT` with new tables/columns/backfill assertions via Testcontainers Postgres. |
| E2E | None dedicated | Existing env-gated AI tests remain optional smoke coverage only. |

## Migration / Rollout

Preferred safe rollout: create `app_user`, add nullable `transaction_entity.owner_id`, and make all new writes populate owner immediately. Existing rows become invisible to normal users until backfilled. If legacy data must remain visible before release, seed a bootstrap user, backfill old rows to that owner in the same release window, then follow with a NOT NULL hardening migration.

## Open Questions

- [x] Should pre-auth legacy transactions be hidden by default or assigned to a bootstrap account before release? Resolved: keep nullable `owner_id`; owner-scoped queries hide legacy rows until an explicit backfill/hardening migration.
- [ ] What session timeout should the MVP use?
- [ ] Do we want `/auth/me` to return CSRF token metadata explicitly, or rely only on the cookie/header convention?
