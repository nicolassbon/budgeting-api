# Archive Report: Unify Amount Unit on Centavos (Backend)

## Change

- **Change**: `unify-amount-unit-centavos`
- **Project**: `budgeting-backend`
- **Archived at**: 2026-07-01
- **Archived to**: `openspec/changes/archive/2026-07-01-unify-amount-unit-centavos/`
- **Mode**: `auto`
- **Strict TDD**: active
- **Artifact store**: `openspec`
- **Skill resolution**: `injected`

## Status

**✅ ARCHIVED.**

The change is fully planned, implemented, verified, and archived. The verify report's final verdict was **COMPLIANT — Ready for archive**; the two previously-flagged CRITICAL blockers (`TransactionService.toOutput` peso regression and missing TDD evidence) were resolved by a follow-up `sdd-apply` step and re-verified. No destructive canonical-spec sync was required because the spec deltas were merged in place during apply, consistent with the project's archive convention (`archive: Merge only finalized deltas into main specs`).

## Executive Summary

The backend now unifies on a single, explicit amount unit — integer centavos — across the domain model, JPA entity, application DTOs, AI draft endpoint, and OpenSpec spec. The internal Java field was renamed from `amount` to `amountCents` (and `totalAmount` to `totalAmountCents` for aggregates), the DB column name is pinned to `amount` via `@Column(name = "amount")` so no destructive Flyway migration was produced, and the AI interpretation prompt now instructs the model to return `amount` in integer centavos. A latent `findHistory` bug was fixed: `totalAmount` (pesos) is now correctly computed as `totalAmountCents / 100.0`. The full Gradle suite is green (77/0/0, 5 env-gated OpenAI IT skipped) and both configured smoke commands pass. Wire field names (`amount` in HTTP/AI, `value` in `TransactionOutput`) and the `persist-transaction` AI tool-call contract are preserved.

## Artifacts Bundled

- `proposal.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (all 19 implementation tasks checked)
- `apply-progress.md` ✅ (TDD Cycle Evidence table present)
- `verify-report.md` ✅ (final verdict: **COMPLIANT — Ready for archive**)
- `archive-report.md` ✅ (this file)

## Spec Deltas Merged in Place

The `transaction-api` spec deltas were merged into `openspec/specs/transaction-api/spec.md` during the apply phase (not at archive time). Per the project's archive convention, finalized deltas are merged in place; the archive step does not re-merge them. The following deltas are now part of the canonical spec:

| Location in spec | Delta |
|------------------|-------|
| `### Requirement: Create transactions` | New global paragraph stating that `amount` in `POST /transactions` requests and in every transaction response is an **integer in centavos (1 peso = 100 centavos)**, that clients MUST divide by 100 to obtain display pesos, and that the same unit applies to the `amount` returned by `POST /transactions/interpret`. |
| `#### Scenario: Preserve /transactions/interpret behavior during extraction` (under "Extract transaction AI orchestration behind a dedicated assistant seam") | New clause: "AND the returned `amount` SHALL be an integer in centavos, consistent with the persisted transaction API." |
| `#### Scenario: Interpretation does not persist by itself` (under "Confirm AI-interpreted persistence before save") | New clause: "AND the returned `amount` SHALL be an integer in centavos (same unit as the persisted transaction API)." |

No `openspec/changes/unify-amount-unit-centavos/specs/transaction-api/spec.md` delta file was created by this change — the change modified the canonical spec directly during apply. No ADDED/MODIFIED/REMOVED requirement operations apply at archive time; the merge happened in place.

## Implementation Summary

| File | Action | Note |
|------|--------|------|
| `src/main/java/dio/budgeting/domain/Transaction.java` | Modified | Field `amount` → `amountCents` (record / class field + constructors + Lombok `@Getter`). |
| `src/main/java/dio/budgeting/domain/TransactionHistoryEntry.java` | Modified | Record component `amount` → `amountCents`. |
| `src/main/java/dio/budgeting/domain/DashboardAggregate.java` | Modified | Outer `totalAmount` → `totalAmountCents`; `CategoryAggregate.totalAmount` → `totalAmountCents`. |
| `src/main/java/dio/budgeting/infraestructure/persistence/entity/TransactionEntity.java` | Modified | Field `amount` → `amountCents`; explicit `@Column(name = "amount")` pins the DB column. |
| `src/main/java/dio/budgeting/infraestructure/persistence/repository/TransactionEntityRepository.java` | Modified | JPQL property paths `t.amount` → `t.amountCents` in the 3 aggregate queries. |
| `src/main/java/dio/budgeting/application/TransactionService.java` | Modified | Uses `getAmountCents()` / `amountCents()`; `toOutput` returns `(double) entry.amountCents()` (centavos); `findHistory` computes `totalAmount = totalAmountCents / 100.0` (pesos). |
| `src/main/java/dio/budgeting/application/output/TransactionOutput.java` | Modified | `from()` uses `transaction.getAmountCents()`; Javadoc on `value` clarifies it is centavos. |
| `src/main/java/dio/budgeting/application/output/DashboardSummaryResponse.java` | Modified | `from()` uses `aggregate.totalAmountCents()`. |
| `src/main/java/dio/budgeting/application/output/CategoryTotalResponse.java` | Modified | `from()` uses `categoryAggregate.totalAmountCents()`. |
| `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` | Modified | Record component `amount` unchanged; class-level Javadoc clarifies integer centavos; `@ToolParam` already says "(en centavos)". |
| `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` | Modified | Javadoc on `amount` component states integer centavos. |
| `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` | Modified | Javadoc on `amount` component states integer centavos. |
| `src/main/java/dio/budgeting/assistant/TransactionAssistantFacade.java` | Modified | `INTERPRETATION_SYSTEM_PROMPT` extended with explicit instruction to return `amount` in integer centavos (e.g. 70 pesos = 7000). |
| `src/main/java/dio/budgeting/assistant/TransactionDraft.java` | Modified | Javadoc on `amount` clarifies it is integer centavos. |
| `openspec/specs/transaction-api/spec.md` | Modified in place | Centavos note under "Create transactions"; explicit centavos clause in `/transactions/interpret`-extraction and "Interpretation does not persist by itself" scenarios. |
| `src/test/java/dio/budgeting/application/TransactionServiceTest.java` | Modified | Updated accessor calls; added `shouldComputeTotalAmountInPesosAsTotalAmountCentsDividedBy100`; added per-item centavos assertions in `shouldComputeHistoryTotalsFromAllRepositoryEntries`; corrected `shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` to expect `500.0` (centavos) for 500-centavo entry. |
| `src/test/java/dio/budgeting/application/DashboardServiceTest.java` | Modified | Updated accessor calls to `totalAmountCents()`. |
| `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java` | Modified | `EXPECTED_INTERPRETATION_PROMPT` updated in lockstep with the new prompt text so `verify(chatClientBuilder).defaultSystem(...)` keeps passing. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Modified | Updated `$.totalAmount.value(27.5)` (pesos); added `shouldReturnAmountInCentavosFromInterpretEndpoint` (asserts `$.amount == 2300`); added HTTP-layer triangulation test `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` that drives a real `TransactionService` (not a mock) through `MockMvc` to pin the per-item centavos contract at the HTTP boundary. |
| `src/test/java/dio/budgeting/infraestructure/http/DashboardControllerTest.java` | Modified | Updated `$.totalAmount.value(57.5)` and `$.topCategories[0].totalAmount.value(35.0)` to pesos; zero-valued assertions kept at `0.0`. |
| `src/test/java/migrationtest/EvolvedTransactionEntity.java` | Modified (test helper) | Field `private long amount` → `private long amountCents`; updated mapper calls. |

`git diff --stat`: 20 files changed, +171 / −53 (224 changed lines) — within the 200–300-line forecast, well under the 400-line budget.

## Validation Snapshot

| Command | Result | Evidence |
|---------|--------|----------|
| `./gradlew test` | ✅ PASS | `BUILD SUCCESSFUL in 46s`; aggregated XML: **tests=77, skipped=5, failures=0, errors=0**. |
| `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` | ✅ PASS | `BUILD SUCCESSFUL`; `TEST-dio.budgeting.BudgetingApplicationTests.xml`: tests=1, failures=0, errors=0. |
| `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` | ✅ PASS | `BUILD SUCCESSFUL`; `TEST-dio.budgeting.FlywayMigrationIT.xml`: tests=3, failures=0, errors=0. |

Skipped tests are the environment-gated OpenAI integration tests (`OpenAiChatClientIT`, `OpenAiChatModelIT`, `OpenAiSpeechModelIT`, `OpenAiTranscriptionModelIT`, `ToolCallingIT`) — skipped without `OPENAI_API_KEY`, as expected and pre-existing.

No new Flyway migration file was added; `src/main/resources/db/migration/` still holds only `V1..V4` (pre-existing).

## Constraints Honored

- **No new Flyway migration under `src/main/resources/db/migration/`.** Only the pre-existing `V1__Initial_schema.sql`, `V2__auth_and_transaction_ownership.sql`, `V3__rename_transaction_categories_to_spanish.sql`, `V4__add_transaction_occurred_at.sql` are present. Rule `apply.require_flyway_for_persistence_changes: true` holds trivially.
- **DB column name preserved as `amount`** (no column rename). `TransactionEntity.java` carries `@Column(name = "amount") private long amountCents;`.
- **`persist-transaction` AI tool-call contract unchanged.** `PersistTransactionInput.amount` (record component, type `long`, `@ToolParam(description = "Valor del gasto (en centavos)")`) is preserved; `TransactionService.create` still passes `input.amount()` straight into `new Transaction(...)`. Confirmed by `TransactionToolContractTest` (3 tests, 0 failures).
- **No commit made by `sdd-apply`.** `git log -1 --oneline` HEAD is `cb5a13f feat(transactions): add owner-scoped PUT /transactions/{id} update endpoint` (pre-existing, not authored by apply). The parent orchestrates commits.
- **`infraestructure` typo preserved.** All new/changed files retain package `dio.budgeting.infraestructure.*`; no renamed packages. Rule `apply.preserve_infraestructure_typo: true` holds.
- **Wire field names preserved.** `TransactionResponse.amount`, `TransactionRequest.amount`, `PersistTransactionInput.amount`, `TransactionDraft.amount`, `TransactionOutput.value` are unchanged in name; only their unit semantics and the internal Java names were clarified.
- **Cross-endpoint wire unit consistency — history per-item `amount` stays integer centavos** (create == category-list == history == /interpret). `toOutput` returns `(double) entry.amountCents()` (no `/100.0`). New HTTP pinning test `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` asserts `$.items[0].amount == 500.0` and `$.items[1].amount == 2250.0`.
- **`findHistory.totalAmount` is in pesos** (only place `/ 100.0` is applied). `application/TransactionService.java` line 87 is the unique `/100.0` site; `toOutput` does NOT divide.
- **`openspec/config.yaml` `archive.preserve_archive_history: true` honored.** This archive report is itself part of the audit trail; the change directory is moved (not deleted) into `openspec/changes/archive/2026-07-01-unify-amount-unit-centavos/`.

## Follow-ups Deferred

| Follow-up | Status | Notes |
|-----------|--------|-------|
| Defensive parse for AI `/transactions/interpret` peso drift | OPEN | If production traffic shows the model still returning pesos despite the prompt change, add a multiply-by-100 heuristic (e.g. when value looks like pesos such as `< 1_000_000`). Out of scope for this change; documented in proposal/design/verify-report. |
| Future `Money` value object for multi-currency | DEFERRED | When multi-currency becomes a requirement, introduce `Money` (or `Amount` + `Currency`) and migrate this rename into it. The current rename makes that future refactor smaller. Out of scope. |
| Coordinated frontend release | OPEN | The frontend `unify-amount-unit-centavos` change in `budgeting-frontend` must be ready to consume integer centavos before the backend ships `/transactions/interpret` cents behavior (or both must ship together). |
| DB column rename `amount` → `amount_cents` | DEFERRED | Explicitly out of scope; would require a destructive Flyway migration. The `@Column(name = "amount")` annotation makes the divergence between Java field and DB column explicit and reviewable. |

## Structured Status / actionContext

- **Status**: `archived`
- **Mode**: `auto`
- **Action context**: workspace-planning (archive is non-mutating beyond the move; no `allowedEditRoots` needed because the move target is inside the authoritative workspace and the change was never committed).
- **Artifact store**: `openspec` (file-backed; archive report is written to disk and the change folder is moved into the dated archive).
- **Engram traceability**: not applicable — `artifactStore: openspec` only. No observation IDs are recorded in this report; the project's Engram store is not used by this change's lifecycle.
- **Dependencies**: coordinated frontend change `unify-amount-unit-centavos` in `budgeting-frontend` (carried as OPEN follow-up; not a blocker for backend archive).
- **Blocked reasons**: none.

## Destructive Merge Approvals

None. This archive performed no canonical-spec sync. The only filesystem changes are: (1) authoring `archive-report.md` in the active change directory, and (2) moving the entire active change directory to `openspec/changes/archive/2026-07-01-unify-amount-unit-centavos/`. Both targets are inside the authoritative workspace.

## Archived Path

`openspec/changes/archive/2026-07-01-unify-amount-unit-centavos/`

Containing: `proposal.md`, `design.md`, `tasks.md`, `apply-progress.md`, `verify-report.md`, `archive-report.md`.

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the next change.

**Recommended next**: start the coordinated frontend change `unify-amount-unit-centavos` in `budgeting-frontend` so that the backend `/transactions/interpret` cents behavior can ship (or both can ship together).
