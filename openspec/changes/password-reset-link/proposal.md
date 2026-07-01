# Proposal: Password Reset Link

## Intent

Add a lean, secure-enough password recovery flow so registered users can reset forgotten passwords through an email link. This preserves the existing email/password auth model and avoids support-only manual recovery.

## Scope

### In Scope
- Public `POST /auth/forgot-password` accepting `{ "email": "..." }` and always returning `202 Accepted`.
- Public `POST /auth/reset-password` accepting `{ "token": "...", "newPassword": "..." }` and returning `204 No Content` on success.
- Time-limited reset token storage, password update with BCrypt, and Resend email delivery.

### Out of Scope
- MFA, magic login, account recovery questions, admin recovery, or frontend implementation.
- Rate limiting beyond basic validation/logging; add later if abuse appears.

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `user-auth`: add password recovery by email reset link.

## Approach

Use a simple `password_reset_token` table with `user_id`, `token_hash`, `expires_at`, `used_at`, and timestamps. Generate a random raw token, store only a SHA-256 hash, expire after ~30 minutes, and mark as used after successful reset. When feasible, invalidate prior unused tokens for the same user before creating a new one.

Add a small email adapter using Spring `RestClient` against Resend (`RESEND_API_KEY`, configured sender such as `no-reply@mail.nidoapp.online`, and reset base URL). The same Resend API key may be reused if the sender domain is verified in the same account.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `infraestructure/http/AuthController.java` | Modified | Add forgot/reset endpoints and DTOs. |
| `application/auth` | Modified | Add recovery orchestration and password update. |
| `domain/user`, persistence repos/entities | Modified | Add save password / token repository support. |
| `src/main/resources/db/migration` | New | Add token table; update Flyway test expectations. |
| `config/SecurityConfig.java` | Modified | Permit public recovery endpoints. |
| `application.properties`, `.env.example` | Modified | Add Resend/reset URL configuration. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| User enumeration | Med | Always return `202`; do not reveal missing emails. |
| Token leakage | Low | Store hashes only; short TTL; single-use. |
| Email config failure | Med | Fail silently to clients, log operational error. |

## Rollback Plan

Disable frontend links/calls, remove Resend env values, revert code and migration before release; if already migrated, leave the unused table harmless or add a follow-up drop migration.

## Dependencies

- Verified Resend sender domain and `RESEND_API_KEY`.
- Frontend reset page URL for link construction.

## Success Criteria

- [ ] Existing auth/session behavior remains unchanged.
- [ ] Known email receives a reset link; unknown email gets the same response.
- [ ] Valid token changes password once; reused/expired tokens fail.
- [ ] `BudgetingApplicationTests`, `FlywayMigrationIT`, and focused auth tests pass.
