# Verification Report: Password Reset Link

**Change**: `password-reset-link`  
**Mode**: Strict TDD  
**Artifact store**: OpenSpec  
**Verified at**: 2026-07-01

## Completeness

| Metric | Value |
|--------|-------|
| Proposal artifacts | `proposal.md`, `tasks.md`, `apply-progress.md` |
| Tasks total | 6 |
| Tasks complete | 6 |
| Tasks incomplete | 0 |
| Design/spec artifacts | Not present; verification used proposal success criteria and tasks |

## Build & Tests Execution

**Focused verification**: Passed

```text
./gradlew test --tests "dio.budgeting.infraestructure.http.AuthControllerTest" --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"

BUILD SUCCESSFUL in 47s
```

**Full suite**: Passed

```text
./gradlew test

BUILD SUCCESSFUL in 57s
Test results parsed from build/test-results/test: 117 tests, 5 skipped, 0 failures, 0 errors across 22 suites.
```

**Coverage**: Not available — this project has no configured coverage task.

## TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD evidence reported | ✅ | `apply-progress.md` includes a TDD Cycle Evidence table. |
| All tasks have tests | ✅ | 2/2 reported task rows have test files. |
| RED confirmed | ✅ | Reported test files exist; historical RED state cannot be rerun after implementation, but the apply evidence records the missing-contract/migration RED steps. |
| GREEN confirmed | ✅ | `AuthControllerTest` and `FlywayMigrationIT` passed during focused execution. |
| Triangulation adequate | ⚠️ | Forgot/reset happy paths and reused token are covered; expired-token behavior is implemented but not covered by a runtime test. |
| Safety net for modified files | ✅ | Baseline safety net was reported and focused/full tests pass now. |

**TDD Compliance**: 5/6 checks passed; 1 warning.

## Test Layer Distribution

| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | 5 existing auth service tests | 1 | JUnit 5, AssertJ |
| Integration | 8 relevant auth/Flyway tests | 2 | Spring Boot Test, MockMvc, Testcontainers PostgreSQL |
| E2E | 0 | 0 | Not configured |
| **Total relevant** | **13** | **3** | |

## Assertion Quality

✅ All reviewed assertions verify real behavior. No tautologies, ghost loops, or smoke-only password-reset tests were found.

## Proposal Compliance Matrix

| Proposal criterion | Evidence | Result |
|--------------------|----------|--------|
| `POST /auth/forgot-password` is public and returns generic `202 Accepted` | `SecurityConfig` permits the endpoint; `AuthControllerTest.shouldAcceptForgotPasswordWithoutRevealingAccountsAndSendOnlyKnownUserResetLink` verifies known and unknown emails both return `202`. | ✅ COMPLIANT |
| `POST /auth/reset-password` accepts token/new password and returns `204` on success | `AuthController.resetPassword`; `AuthControllerTest.shouldResetPasswordOnceWithValidToken` verifies `204`. | ✅ COMPLIANT |
| Known email receives reset link; unknown email gets same response | Capturing mail sender receives exactly one email for the known account while both requests return `202`. | ✅ COMPLIANT |
| Tokens are stored hashed only | `PasswordResetService.hash` SHA-256 hashes the raw token before saving; migration stores `token_hash`; raw token is only placed in the email link. | ✅ COMPLIANT |
| Token TTL is implemented | `auth.password-reset.token-ttl` default is `30m`; `PasswordResetToken.isUsableAt` rejects expired tokens. | ⚠️ PARTIAL — implemented, but no passed test covers expiry. |
| Token is single-use | `resetPassword` marks token used after successful reset; reused token returns `401 reset_token_invalid` in integration test. | ✅ COMPLIANT |
| Prior unused tokens invalidated when feasible | `PasswordResetService.createAndSendResetLink` calls `markUnusedTokensUsedForUser` before saving a new token. | ✅ COMPLIANT |
| Password update uses BCrypt | `SecurityConfig.passwordEncoder` is `BCryptPasswordEncoder`; reset saves `passwordEncoder.encode(newPassword)` and login with new password passes. | ✅ COMPLIANT |
| Resend delivery is configurable | `resend.api-key`, `resend.sender`, `resend.base-url`, `PASSWORD_RESET_BASE_URL`, and `PASSWORD_RESET_TOKEN_TTL` are in properties and `.env.example`; adapter posts to `/emails` with `Authorization: Bearer ...`. | ✅ COMPLIANT |
| Flyway schema aligns with JPA | `V6__add_password_reset_token.sql` creates the mapped table/columns; `FlywayMigrationIT` verifies version `6` and columns; full startup tests pass with `ddl-auto=validate`. | ✅ COMPLIANT |

## Correctness / Static Evidence

| Area | Status | Notes |
|------|--------|-------|
| Endpoint behavior | ✅ | Forgot password is generic for existing/missing accounts; reset succeeds with valid token and fails on reuse. |
| Token generation | ✅ | 32 random bytes from `SecureRandom`, Base64 URL-safe no padding. |
| Token hashing | ✅ | SHA-256 hash stored as Base64; acceptable for high-entropy random reset tokens. |
| TTL | ⚠️ | Code enforces `expiresAt.isAfter(now)` and configurable 30-minute default, but runtime coverage is missing. |
| Single-use | ✅ | Used tokens are filtered out; successful reset persists `used_at`. |
| Persistence | ✅ | Entity, repository, migration, and Flyway assertions align. |
| Resend shape | ✅ | Resend docs allow `POST /emails` with `from`, `to` as string or string array, `subject`, and `text`; implementation matches this shape. |
| Operational secrecy | ✅ | API key is externally configured and not hardcoded. |

## Issues Found

### CRITICAL

None.

### WARNING

1. Expired reset tokens are implemented but not covered by a deterministic runtime test. The proposal success criteria explicitly include expired-token failure, so this should be added before treating the flow as fully covered.
2. Concurrent reuse is not guarded with a database-level conditional update or pessimistic lock. Two simultaneous reset attempts with the same token could theoretically both pass the in-memory usability check before `used_at` is persisted. Low likelihood for this first version, but worth hardening later.

### SUGGESTION

1. Add a small clock-controlled service or integration test for expired token rejection.
2. Consider adding an idempotency key to Resend sends in a future hardening pass.
3. Consider a partial unique index for active tokens only if future requirements need at most one active reset token per user at the database level; current service-level invalidation matches the lean proposal.

## Verdict

**PASS WITH WARNINGS**

The implementation matches the lean proposal and is safe enough for a first version: generic forgot-password response, hashed high-entropy tokens, 30-minute TTL implementation, single-use semantics, Flyway/JPA alignment, configurable Resend integration, and deterministic passing tests. The main gap is missing runtime coverage for expired-token rejection, plus a known concurrency hardening opportunity.
