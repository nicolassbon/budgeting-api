# Proposal: Secure Login Authentication

## Intent

Add server-side login and authorization so transaction data and AI/audio communication endpoints are not public. This matters because `/transactions/ai`, `/transactions/interpret`, and root `/api/*` AI endpoints can spend OpenAI quota, process uploaded audio, and expose or mutate transaction data without identity or ownership checks today.

## Scope

### In Scope
- Email/password MVP authentication with server-side sessions and httpOnly cookies.
- User-owned transaction access: create/list/tool calls must operate only for the authenticated user.
- Protect cost-sensitive AI/audio endpoints while preserving existing paths and response contracts.
- Flyway migration plan for users, roles if needed, session/auth schema, and transaction ownership.
- Strict TDD coverage for login, protected/anonymous access, ownership isolation, and migration validation.

### Out of Scope
- Google/OAuth login, except as a later extension point.
- Admin roles/features unless required to support basic user records.
- Endpoint renames, including `/api/sinthesize`, `/transactions/ai`, and `/transactions/interpret`.

## Capabilities

### New Capabilities
- `user-auth`: Email/password registration/login/logout, session lifecycle, httpOnly-cookie handling, password hashing, and authenticated principal access.

### Modified Capabilities
- `transaction-api`: Require authentication and enforce per-user ownership for manual and AI/tool transaction operations.
- `assistant-demo-api`: Require authentication for AI/demo endpoints to reduce cost and availability abuse while preserving routes.

## Approach

Use Spring Security with BCrypt, server-side sessions, secure cookie settings, CSRF decisions documented in design, and service-boundary ownership checks. Add Flyway migrations before JPA validation. Keep controllers thin; enforce ownership in application services/tool-facing methods, not only request filters.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `build.gradle` | Modified | Add Spring Security/session auth dependencies. |
| `src/main/resources/db/migration` | New | Add users and transaction ownership migrations. |
| `src/main/java/dio/budgeting/config` | Modified | Security/session configuration and endpoint matchers. |
| `src/main/java/dio/budgeting/{domain,application,infraestructure}` | Modified | User model, ownership enforcement, protected adapters/tools. |
| `src/test/java/dio/budgeting` | Modified | Auth, authorization, and migration tests. |

## Open Questions

- Should registration be open, invite-only, or seeded for MVP?
- What session duration and logout behavior are acceptable?
- Should demo root AI endpoints be authenticated in all profiles or disabled outside dev?

## Review Workload Forecast

Single PR is plausible but near the 800-line budget. If design grows beyond auth foundation + endpoint protection, split Google/admin/rate-limits out.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| IDOR/BOLA via missed ownership check | High | Enforce at service/tool boundary and test cross-user access. |
| Multipart/AI endpoint filter regressions | Med | Add focused security tests for AI routes. |
| Flyway/JPA startup failure | Med | Migration tests with `FlywayMigrationIT`. |

## Rollback Plan

Revert the auth/security commit and migrations before release. If already deployed, disable protected AI exposure at the edge and restore a database backup or forward migration that removes ownership constraints only after data export.

## Dependencies

- Spring Security, BCrypt password encoding, existing PostgreSQL/Flyway setup.

## Success Criteria

- [ ] Anonymous users cannot call transaction or AI endpoints.
- [ ] Authenticated users cannot read/write another user's transactions.
- [ ] Login/logout use httpOnly cookies and encoded passwords.
- [ ] `./gradlew test` passes, including migration and auth/security tests.
