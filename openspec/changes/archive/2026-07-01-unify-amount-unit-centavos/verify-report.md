# Verification Report: Unify Amount Unit on Centavos (Backend)

## Change

- Change ID: `unify-amount-unit-centavos`
- Project: `budgeting-backend`
- Mode: `auto`
- Strict TDD: active (`openspec/config.yaml` → `strict_tdd: true`)
- Test runner: `./gradlew test`
- Artifact store: `openspec`
- This report supersedes the previous `verify-report.md` (which flagged two CRITICAL blockers). A follow-up `sdd-apply` step resolved both blockers; this re-verification confirms they are closed.

## Final Verdict

**✅ COMPLIANT — Ready for archive.**

The Gradle suite is green [`./gradlew test` → `BUILD SUCCESSFUL in 46s`, aggregated XML: tests=77, skipped=5 (env-gated OpenAI IT), failures=0, errors=0; both `smoke_commands` pass]. Both previously-flagged CRITICAL blockers are resolved:

1. `apply-progress.md` is now present and contains a TDD evidence table for audit.
2. `TransactionService.toOutput` no longer divides by `100`; it returns `(double) entry.amountCents()` so the history per-item wire `amount` stays integer centavos, consistent with `POST /transactions`, `GET /transactions/{category}`, and the spec. A new HTTP-layer pinning test (`shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos`) locks this at the controller, driving the real `TransactionService` (not a mock that would bypass the regression).

All proposal success criteria and all hard constraints hold.

## Executive Summary

The rename to `amountCents`, DB-column pinning via `@Column(name = "amount")`, AI prompt centavos instruction, `TransactionDraft`/`PersistTransactionInput`/`TransactionRequest`/`TransactionResponse` Javadoc, spec edit, `findHistory.totalAmount` peso fix, the corrected `toOutput` centavos contract, and the new focused tests are all in place. The full Gradle suite is green (77 tests, 0 failures, 0 errors, 5 env-gated skips). No new Flyway migration was introduced; the DB column stays `amount`; the `persist-transaction` AI tool contract is unchanged; no commit was authored by the apply step. The change is ready for `sdd-archive`.

## Re-validation of Previous Blockers

| # | Previous blocker | Status | Evidence |
|---|---|---|---|
| 1 | History per-item wire `amount` regressed from centavos to pesos via `TransactionService.toOutput` dividing by `100` | ✅ RESOLVED | `application/TransactionService.java:100` now reads `(double) entry.amountCents()` (no `/100.0`). New HTTP pinning test `TransactionControllerTest.shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` (`infraestructure/http/TransactionControllerTest.java:375`) drives a real `TransactionService` with a `FakeTransactionRepositoryForControllerTest` and asserts `$.items[0].amount == 500.0` and `$.items[1].amount == 2250.0` (centavos), `$.totalAmount == 27.5` (pesos). Cross-endpoint consistency confirmed: create/category-list/history all return per-item `amount` in centavos. |
| 2 | Missing `apply-progress.md` / no TDD Cycle Evidence (strict TDD cannot be audited) | ✅ RESOLVED | `openspec/changes/unify-amount-unit-centavos/apply-progress.md` is present (13 KB). Contains a `## TDD evidence (strict TDD was active)` table with one row per task (RED step / GREEN step / TRIANGULATE–REFACTOR), a `## Files changed` list, a `## Test commands run` section, a `## Deviations from design` section, and `## Structured status / actionContext`. Honest about which steps were mechanical renames (safety net = compile checkpoint) vs. test-driven behavioral changes. |

## Validation Results

| Command | Outcome | Evidence |
|---|---:|---|
| `./gradlew test` (verify.test_command) | ✅ PASS | `BUILD SUCCESSFUL in 46s`; aggregated XML: tests=77, skipped=5, failures=0, errors=0. No `in-progress-results-*.bin` `NoSuchFileException` warning this run (cleaned state). |
| `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` (smoke) | ✅ PASS | `BUILD SUCCESSFUL in 10s`; `TEST-dio.budgeting.BudgetingApplicationTests.xml`: tests=1, failures=0, errors=0. |
| `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` (smoke) | ✅ PASS | `BUILD SUCCESSFUL in 22s`; `TEST-dio.budgeting.FlywayMigrationIT.xml`: tests=3, failures=0, errors=0. |

Skipped tests are the environment-gated OpenAI integration tests (`OpenAiChatClientIT`, `OpenAiChatModelIT`, `OpenAiSpeechModelIT`, `OpenAiTranscriptionModelIT`, `ToolCallingIT`) — skipped without `OPENAI_API_KEY`, as expected.

## Success Criteria (from proposal.md)

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | `./gradlew test` passes | ✅ | 77 tests, 0 failures, 0 errors (see Validation Results). |
| 2 | `Transaction.amountCents` is the field name across the domain, JPA entity, application inputs/outputs, and services | ✅ | `domain/Transaction.java` field + all constructors renamed to `amountCents` (`amount` count = 0); `TransactionEntity.amountCents` with `@Column(name = "amount")` (`infraestructure/persistence/entity/TransactionEntity.java:23-24`); `TransactionHistoryEntry.amountCents`; `DashboardAggregate.totalAmountCents` (outer + `CategoryAggregate`); `TransactionService` calls `getAmountCents()` / `amountCents()`; repository JPQL uses `t.amountCents` (3 references in `TransactionEntityRepository`); `migrationtest/EvolvedTransactionEntity` follows. |
| 3 | `findHistory` returns `totalAmount == totalAmountCents / 100.0` (pesos), verified by a focused test | ✅ | `application/TransactionService.java:87` `double totalAmount = totalAmountCents / 100.0;`; `TransactionServiceTest.shouldComputeTotalAmountInPesosAsTotalAmountCentsDividedBy100` (`application/TransactionServiceTest.java:208`) asserts `57.5 == 5750L / 100.0`; `shouldComputeHistoryTotalsFromAllRepositoryEntries` (`:181`) asserts `57.5` and `response.totalAmount() == response.totalAmountCents() / 100.0`. |
| 4 | `/transactions/interpret` returns `amount` as an integer centavos value per the updated system prompt | ✅ | `assistant/TransactionAssistantFacade.java` `INTERPRETATION_SYSTEM_PROMPT` now says "el monto numérico (siempre en centavos como entero, por ejemplo 70 pesos = 7000, 12.30 pesos = 1230, 1 peso = 100)"; `TransactionControllerTest.shouldReturnAmountInCentavosFromInterpretEndpoint` (`:325`) asserts `$.amount == 2300`; `TransactionAssistantFacadeTest.EXPECTED_INTERPRETATION_PROMPT` updated in lockstep and verified by `verify(chatClientBuilder).defaultSystem(...)`. |
| 5 | `spec.md` explicitly states `amount` is integer centavos in create, list-by-category, history, and `/interpret`, and that clients divide by 100 for display pesos | ✅ | `openspec/specs/transaction-api/spec.md`: global note under "Create transactions" requiring "integer in centavos (1 peso = 100 centavos). Clients MUST divide ... same unit applies to the `amount` returned by the `POST /transactions/interpret` draft endpoint"; explicit centavos clause added to the `/interpret`-extraction scenario and to the "Interpretation does not persist by itself" scenario. Implementation matches: create/category-list/history all emit per-item centavos; `totalAmount` emits pesos. |
| 6 | DB column name remains `amount`; no new Flyway migration is introduced | ✅ | `src/main/resources/db/migration/` holds only `V1..V4` (pre-existing); no `V5__*.sql`. `infraestructure/persistence/entity/TransactionEntity.java:23` carries `@Column(name = "amount")`. |
| 7 | `persist-transaction` AI tool-call behavior and contract unchanged | ✅ | `application/input/PersistTransactionInput.java` keeps record component `long amount` and `@ToolParam(description = "Valor del gasto (en centavos)")`; `TransactionService.create` still passes `input.amount()` straight into `new Transaction(...)`. Only Javadoc added on the record. Confirmed by `TransactionToolContractTest` (3 tests, 0 failures). |

## Constraints

| Constraint | Status | Evidence |
|---|---:|---|
| No new Flyway migration under `src/main/resources/db/migration/` | ✅ | Only `V1__Initial_schema.sql`, `V2__auth_and_transaction_ownership.sql`, `V3__rename_transaction_categories_to_spanish.sql`, `V4__add_transaction_occurred_at.sql`; no `V5`. |
| DB column name preserved as `amount` (no column rename) | ✅ | `infraestructure/persistence/entity/TransactionEntity.java:23-24` `@Column(name = "amount") private long amountCents;`. |
| `persist-transaction` AI tool-call contract unchanged (`long amount`/`amountCents` semantics, `@ToolParam` says centavos) | ✅ | `application/input/PersistTransactionInput.java` param name `amount`, type `long`, description `"Valor del gasto (en centavos)"`; `create()` uses it directly. |
| No commit made by the apply step | ✅ | `git status --short` shows 21 modified files plus untracked `openspec/changes/unify-amount-unit-centavos/`; `git log -1 --oneline` HEAD = `cb5a13f feat(transactions): add owner-scoped PUT /transactions/{id} update endpoint` (pre-existing, not authored by apply). |
| `infraestructure` typo preserved | ✅ | All new/changed files retain package `dio.budgeting.infraestructure.*`; no renamed packages. |
| Wire field names preserved (`amount` in HTTP/AI, `value` in `TransactionOutput`) | ✅ | `TransactionResponse.amount`, `TransactionRequest.amount`, `PersistTransactionInput.amount`, `TransactionDraft.amount`, `TransactionOutput.value` unchanged in name. |
| Cross-endpoint wire unit consistency — history per-item `amount` stays integer centavos (create == category-list == history) | ✅ | `toOutput` returns `(double) entry.amountCents()` (centavos). New HTTP test `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` asserts `$.items[0].amount == 500.0`, `$.items[1].amount == 2250.0`. `POST /transactions` and `GET /transactions/{category}` use `TransactionOutput.from` which does `BigDecimal.valueOf(getAmountCents()).setScale(2).doubleValue()` (centavos). All three endpoints centavos. |
| `findHistory.totalAmount` is in pesos (only place the `/ 100.0` division is applied) | ✅ | `application/TransactionService.java:87` is the only `/100.0`; `toOutput` does NOT divide. |

## Spec Compliance Matrix (transaction-api)

| Requirement / Scenario | Status | Notes |
|---|---:|---|
| Create a transaction successfully (`201`, fields `id/description/category/amount/date`) | ✅ | `TransactionControllerTest` create tests pass; `amount` in centavos. |
| `amount` is integer centavos, clients divide by 100 (global note + scenario clauses) | ✅ | Spec note present at `transaction-api/spec.md`; implementation matches across create/category-list/history. |
| List all transactions for current user (history items with `amount`) | ✅ | History item `amount` in centavos (new HTTP pinning test). |
| List transactions by category (with `amount`) | ✅ | Uses `TransactionOutput.from` (centavos); tests pass. |
| History totals (`totalAmountCents` integer, `totalAmount` pesos, `transactionCount`) | ✅ | `totalAmount = totalAmountCents / 100.0` correctly pesos; tests pin `27.5`. |
| Preserve `/transactions/interpret` behavior — `amount` integer centavos | ✅ | Prompt + `shouldReturnAmountInCentavosFromInterpretEndpoint` pin centavos (`$.amount == 2300`). |
| Interpretation does not persist by itself; `amount` in centavos | ✅ | Test asserts `$.amount` integer centavos; no transaction persisted. |
| Owner-scoped / auth enforcement unchanged | ✅ | No security code touched; `SecurityEndpointIntegrationTest` (10 tests) and `AuthControllerTest` pass. |

## Strict TDD Compliance

| Check | Result | Details |
|---|---:|---|
| `apply-progress.md` artifact present | ✅ | `openspec/changes/unify-amount-unit-centavos/apply-progress.md` (13 KB). |
| TDD evidence table present in apply-progress | ✅ | `## TDD evidence (strict TDD was active)` table: one row per task (1.1 → 5.x), columns `Task / RED / GREEN / TRIANGULATE-REFACTOR`. Honest about mechanical renames (safety net = compile checkpoint) vs. test-driven behavioral changes. |
| Tests exist for changed behavior | ✅ | New `shouldComputeTotalAmountInPesosAsTotalAmountCentsDividedBy100`, `shouldReturnAmountInCentavosFromInterpretEndpoint`, `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos`; updated `shouldComputeHistoryTotalsFromAllRepositoryEntries`, `shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange`, dashboard/controllers/assistant tests. |
| Tests pass (GREEN still true) | ✅ | `./gradlew test` 77/0/0, 5 skipped. |
| Assertion quality — no tautologies / smoke-only / type-only | ✅ | New history HTTP test deliberately bypasses the service mock and drives a real `TransactionService` through `MockMvc` so the `toOutput` unit is pinned at the HTTP layer (avoids the previous masking gap). |

## Review Workload / PR Boundary

| Field | Finding |
|---|---|
| Forecast in `tasks.md` | single-pr, ~200–300 lines, low 400-line risk, `stacked-to-main`, chained PRs: No. |
| Actual diff | `git diff --stat`: 21 files, +203 / −53 (256 changed lines) — within budget. |
| PR boundary | Matches single-PR recommendation. No chained PRs needed. |
| Scope creep | The scope creep from the first apply run (the unauthorized `toOutput` `/100.0`) has been reverted; a defensive pinning test was added to lock the per-item centavos contract and prevent recurrence. |

## Risks and Follow-ups (carried from proposal/design)

| Risk / Follow-up | Status |
|---|---|
| AI model returns pesos from `/transactions/interpret` despite the prompt change | OPEN follow-up: add a defensive parse (e.g., multiply-by-100 heuristic when value looks like pesos) if production shows drift. Out of scope for this change. |
| Frontend out of sync if backend ships first | OPEN: coordinate with `budgeting-frontend` `unify-amount-unit-centavos`; withhold the `/interpret` centavos toggle until the frontend adjuster is ready, or ship together. |
| Future `Money` value object (multi-currency) | Deferred; the rename makes that future refactor smaller. Out of scope. |
| Hidden precision change from the history fix | RESOLVED: the `/100.0` is now restricted to the `totalAmount` line (per design); per-item `amount` stays centavos. |

## Sign-off

**Ready for archive.** All proposal success criteria (7/7) and all hard constraints are met. The two previously-flagged CRITICAL blockers are resolved and re-verified. The change can proceed to `sdd-archive`, which will migrate the deltas in `openspec/changes/unify-amount-unit-centavos/` (proposal/design/tasks/apply-progress/verify-report) into the immutable `openspec/changes/archive/` audit trail and, per the `transaction-api` spec deltas, merge any final wording into `openspec/specs/transaction-api/spec.md` if not already merged in place.