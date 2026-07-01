# Apply Progress: Password Reset Link

## Mode

Strict TDD

## Completed Tasks

- [x] Add public forgot-password endpoint with generic `202 Accepted` response.
- [x] Add public reset-password endpoint with token and new password handling.
- [x] Store short-lived password reset tokens as hashes and mark them single-use.
- [x] Integrate password reset email delivery through configurable Resend settings.
- [x] Add the minimal Flyway schema migration for reset tokens.
- [x] Add focused tests for externally visible behavior and core failure paths.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| Password reset link flow | `src/test/java/dio/budgeting/infraestructure/http/AuthControllerTest.java` | Integration | ✅ `AuthServiceTest`, `AuthControllerTest`, `FlywayMigrationIT` baseline passed | ✅ Compile failed on missing password reset mail contract before implementation | ✅ `AuthControllerTest` passed | ✅ Known/unknown forgot-password, valid reset, login with new password, and reused token failure covered | ✅ Kept flow in application service plus persistence/email adapters |
| Password reset migration | `src/test/java/dio/budgeting/infraestructure/persistence/FlywayMigrationIT.java` | Integration | ✅ `FlywayMigrationIT` baseline passed | ✅ Migration expectation changed before `V6` implementation | ✅ `FlywayMigrationIT` passed | ✅ Startup migration and schema validation expectations include version 6 | ✅ Minimal table/index only |

## Test Summary

- **Total tests written**: 2 new integration tests plus Flyway assertions for the reset token table.
- **Total tests passing**: Full `./gradlew test` passed.
- **Layers used**: Integration.
- **Approval tests**: None — no refactoring-only task.
- **Pure functions created**: 1 (`PasswordResetToken.isUsableAt`).

## Verification

- ✅ `./gradlew test --tests "dio.budgeting.application.auth.AuthServiceTest" --tests "dio.budgeting.infraestructure.http.AuthControllerTest" --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"` passed as safety net.
- ✅ `./gradlew test --tests "dio.budgeting.infraestructure.http.AuthControllerTest"` failed RED before production implementation, then passed GREEN after implementation.
- ✅ `./gradlew test --tests "dio.budgeting.application.auth.AuthServiceTest" --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"` passed.
- ✅ `./gradlew test` passed.

## Deviations

None — implementation matches the proposal's lean approach.

## Issues

- `openspec/changes/password-reset-link` only had a proposal when apply started; tasks and apply-progress were created during apply to persist completion state.
