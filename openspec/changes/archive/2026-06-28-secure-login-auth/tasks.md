# Tasks: Secure Login Authentication

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 700-950 |
| 400-line budget risk | High |
| 800-line budget outlook | Tight; likely exceeded with migration + security + tests |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 auth+migration → PR 2 ownership → PR 3 protected AI/tests |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Session auth, user schema, `/auth/*`, CSRF bootstrap | PR 1 | Smallest releasable auth slice with tests/migration |
| 2 | Owner-scoped transactions and legacy rollout rule | PR 2 | Depends on Unit 1; confirm hidden-vs-backfill decision first |
| 3 | Protect `/transactions/ai`, `/transactions/interpret`, `/api/*` | PR 3 | Depends on Unit 1+2; keep contract tests in same slice |

## Phase 1: RED Foundation

- [x] 1.1 Add failing migration/auth coverage in `src/test/java/dio/budgeting/FlywayMigrationIT.java` for `app_user`, `transaction_entity.owner_id`, default `USER`, and legacy-row rollout expectation.
- [x] 1.2 Add failing auth web tests in `src/test/java/dio/budgeting/infraestructure/http/AuthControllerTest.java` for register, duplicate email, login session, `/auth/me`, logout, and 401 on bad credentials.
- [x] 1.3 Add failing security access tests in `src/test/java/dio/budgeting/assistant/AssistantDemoControllerTest.java` and `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` for anonymous `401` and authenticated contract preservation.

## Phase 2: GREEN Auth Foundation

- [x] 2.1 Create `src/main/java/dio/budgeting/config/SecurityConfig.java` and update `build.gradle` for Spring Security session auth, CSRF cookie repo, endpoint rules, and `401` entry point.
- [x] 2.2 Create `src/main/java/dio/budgeting/domain/user/*`, `src/main/java/dio/budgeting/application/auth/*`, `src/main/java/dio/budgeting/infraestructure/persistence/repository/*User*`, and `src/main/java/dio/budgeting/infraestructure/http/AuthController.java` for BCrypt register/login/logout/me flows.
- [x] 2.3 Add `src/main/resources/db/migration/V2__auth_and_transaction_ownership.sql` for `app_user`, nullable `owner_id`, role defaulting, and the chosen legacy-row handling.

## Phase 3: RED→GREEN Ownership Enforcement

- [x] 3.1 Extend `src/test/java/dio/budgeting/application/TransactionServiceTest.java` and add persistence/integration coverage for owner-scoped create/list, authenticated confirmation save, and cross-user `403` semantics.
- [x] 3.2 Create `src/main/java/dio/budgeting/application/security/AuthenticatedUserProvider.java` plus `src/main/java/dio/budgeting/infraestructure/security/SecurityContextAuthenticatedUserProvider.java`; update `src/main/java/dio/budgeting/application/TransactionService.java` to resolve owner internally.
- [x] 3.3 Modify `src/main/java/dio/budgeting/domain/Transaction.java`, `TransactionRepository.java`, `infraestructure/persistence/entity/TransactionEntity.java`, `JpaTransactionRepository.java`, and `TransactionEntityRepository.java` so every query/write is owner-scoped below controllers.

## Phase 4: Integration, Refactor, Verify

- [x] 4.1 Update `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` and root `/api/*` controllers only as needed to preserve paths/media types while relying on Spring Security for authn.
- [x] 4.2 Refactor duplicated auth/owner test setup, then run `./gradlew test`, plus focused `BudgetingApplicationTests` and `FlywayMigrationIT` smoke checks.

## Release Prerequisite

- [x] Decide before apply whether pre-auth transactions stay hidden until backfill or are assigned to a bootstrap owner during release.
