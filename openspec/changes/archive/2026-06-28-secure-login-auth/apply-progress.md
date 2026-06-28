# Apply Progress: secure-login-auth

## Mode

Strict TDD — `./gradlew test`.

## Completed Tasks

- [x] 1.1 Migration/auth schema coverage updated in `FlywayMigrationIT`.
- [x] 1.2 Auth web/session coverage added in `AuthControllerTest`.
- [x] 1.3 Anonymous protected access coverage added for `/auth/me`, `/transactions/{category}`, `/api/chat-client`, protected transaction POST/multipart routes, and all root assistant `/api/*` routes.
- [x] 2.1 Spring Security session config, CSRF cookie repository, endpoint rules, and 401 entry point added.
- [x] 2.2 User domain, auth service, persistence adapter, and `/auth/*` controller added with BCrypt password hashing.
- [x] 2.3 `V2__auth_and_transaction_ownership.sql` added for `app_user` and nullable transaction `owner_id`.
- [x] 3.1 Transaction service and integration tests now prove owner id is resolved for create/list and two-user same-category listings do not leak another user's rows.
- [x] 3.2 `AuthenticatedUserProvider` port and `SecurityContextAuthenticatedUserProvider` adapter added.
- [x] 3.3 Transaction domain, entity, and repositories now carry owner scope below controllers.
- [x] 4.1 Existing endpoint paths are preserved and protected by the security filter chain.
- [x] 4.2 Focused smoke tests and full Gradle suite passed after verification-gap fixes.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 / 2.3 | `src/test/java/dio/budgeting/FlywayMigrationIT.java` | Integration | ✅ Existing Flyway assertions known | ✅ Failed until V2 migration + entities existed | ✅ `FlywayMigrationIT` passed | ✅ app_user + owner_id + version assertions | ✅ Shared helper retained |
| 1.2 / 2.2 | `src/test/java/dio/budgeting/infraestructure/http/AuthControllerTest.java` | Integration | N/A new test | ✅ Failed before auth controller/security/session implementation | ✅ `AuthControllerTest` passed | ✅ register/login/me/logout + duplicate + bad credentials | ✅ Testcontainers setup extracted via dynamic properties |
| 1.3 / 2.1 / 4.1 | `src/test/java/dio/budgeting/infraestructure/http/AuthControllerTest.java` | Integration | ✅ Existing controller contract tests passed | ✅ Anonymous protected endpoint assertions failed before Spring Security | ✅ `AuthControllerTest` passed | ✅ `/auth/me`, `/transactions/GROCERIES`, `/api/chat-client` | ➖ None needed |
| 3.1 / 3.2 / 3.3 | `src/test/java/dio/budgeting/application/TransactionServiceTest.java` | Unit | ✅ Initial compile/test failed against old constructor/repository contract | ✅ Owner expectations failed before provider/repository changes | ✅ `TransactionServiceTest` passed | ✅ create owner + list owner | ✅ Constructor and fake repository updated |
| Verification gaps: anonymous POST/multipart + assistant protection + two-user ownership | `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java` | Integration | ✅ Baseline command passed: `AuthControllerTest` + `TransactionControllerTest` + `AssistantDemoControllerTest` + `TransactionServiceTest` | ✅ Tests written before production changes; initial focused run failed on missing observable httpOnly session cookie | ✅ Focused `SecurityEndpointIntegrationTest` + `TransactionControllerTest` passed | ✅ anonymous transaction POST, transaction multipart, interpret, all root `/api/*` endpoints; authenticated assistant/AI compatibility; two-user same-category isolation | ✅ Shared register/login/audio helpers kept setup readable |
| Verification gap: interpretation must not persist by itself | `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Controller standalone | ✅ Baseline command passed before edit | ✅ Added explicit `verifyNoInteractions(transactionService)` before production changes | ✅ Focused controller test passed | ✅ Existing response-shape assertions + no-persistence assertion | ➖ None needed |
| Verification gap: httpOnly session cookie | `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java`; `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` | Integration | ✅ Baseline command passed before edit | ✅ `shouldCreateHttpOnlySessionCookieOnLogin` failed before explicit cookie emission | ✅ Focused security integration test passed after adding explicit `Set-Cookie` | ✅ Session reuse remains covered by existing `/auth/me` tests; cookie attribute is now directly asserted | ✅ Extracted `sessionCookie(...)` helper |

## Test Summary

- Baseline safety net: `./gradlew test --tests "dio.budgeting.infraestructure.http.AuthControllerTest" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.assistant.AssistantDemoControllerTest" --tests "dio.budgeting.application.TransactionServiceTest"` — PASS (`BUILD SUCCESSFUL in 22s`).
- RED check: `./gradlew test --tests "dio.budgeting.infraestructure.http.SecurityEndpointIntegrationTest" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest"` — FAIL as expected on `shouldCreateHttpOnlySessionCookieOnLogin` before the cookie fix.
- Focused GREEN: `./gradlew test --tests "dio.budgeting.infraestructure.http.SecurityEndpointIntegrationTest" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest"` — PASS (`BUILD SUCCESSFUL in 20s`).
- Focused verification suite: `./gradlew test --tests "dio.budgeting.infraestructure.http.SecurityEndpointIntegrationTest" --tests "dio.budgeting.infraestructure.http.AuthControllerTest" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.FlywayMigrationIT" --tests "dio.budgeting.BudgetingApplicationTests"` — PASS (`BUILD SUCCESSFUL in 39s`).
- Full suite: `./gradlew test` — PASS (`BUILD SUCCESSFUL in 39s`). JUnit XML summary: 43 tests, 5 skipped, 0 failures, 0 errors across 15 report files.

## Deviations / Notes

- The release prerequisite was resolved before apply: legacy transactions remain with nullable `owner_id` and are hidden from owner-scoped queries until explicit backfill/hardening.
- Cross-user `403` is not runtime-testable in the current HTTP API because no transaction route accepts a transaction id or otherwise targets another user's existing transaction. The transaction spec and design now document this route-shape mismatch: current behavior proves owner-scoped isolation, and any future targeted transaction route must return `403 Forbidden` on owner mismatch.
- The `JSESSIONID` cookie remains the server-side servlet session cookie. Login now emits an explicit httpOnly `Set-Cookie` header with `SameSite=Lax`, while keeping the Spring Security context stored server-side in the HTTP session.

## Workload / PR Boundary

- Mode: single PR with maintainer-approved `size:exception`.
- Boundary: complete `secure-login-auth` implementation plus verification-gap closure in one review slice.
