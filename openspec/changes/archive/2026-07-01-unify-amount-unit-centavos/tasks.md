# Tasks: Unify Amount Unit on Centavos (Backend)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 200–300 (rename + Javadoc + JPQL + 1 new service test + 1 new controller test + a handful of assertion updates) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR (single-pr) — mechanical rename, fits one review session |
| Delivery strategy | single-pr |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: stacked-to-main
400-line budget risk: Low

### Notes

- No Flyway migration is introduced. The DB column `amount` stays; `TransactionEntity` gets `@Column(name = "amount")` to make the contract explicit.
- Wire field names (`amount` in HTTP/AI payload, `value` in `TransactionOutput`) stay unchanged; only the unit semantics and the internal Java names change.
- The `persist-transaction` AI tool-call contract is untouched (already centavos).
- Ship coordinated with the frontend `unify-amount-unit-centavos` change in `budgeting-frontend/openspec/changes/`; do not toggle `/transactions/interpret` to centavos ahead of the frontend.

## Phase 1: Domain rename (RED-light → GREEN)

- [x] 1.1 Rename `Transaction.amount` → `Transaction.amountCents` in `src/main/java/dio/budgeting/domain/Transaction.java` (field plus all 5 constructor parameters; Lombok `@Getter` regenerates `getAmountCents()`).
  - File: `domain/Transaction.java`
  - Verification: `./gradlew compileJava` — expect compile errors at every caller of `Transaction.getAmount()`. This is the RED step.

- [x] 1.2 Propagate the rename to every Java caller and to the JPQL strings, then re-run the full compile.
  - Files:
    - `infraestructure/persistence/entity/TransactionEntity.java` — update `from()`, `toDomain()`, `toHistoryEntry()` to use `transaction.getAmountCents()` / `this.amountCents`.
    - `application/TransactionService.java` — replace `transaction.getAmount()` with `transaction.getAmountCents()`; replace `TransactionHistoryEntry::amount` with `TransactionHistoryEntry::amountCents` and `entry.amount()` with `entry.amountCents()`.
    - `application/output/TransactionOutput.java` — update `from()` to use `transaction.getAmountCents()`.
    - `src/test/java/migrationtest/EvolvedTransactionEntity.java` — rename `private long amount` → `private long amountCents`.
    - `src/test/java/.../TransactionServiceTest.java` — `getAmount()` → `getAmountCents()` and `entry.amount()` → `entry.amountCents()`.
    - `infraestructure/persistence/repository/TransactionEntityRepository.java` — change JPQL property path `t.amount` → `t.amountCents` in the 3 aggregate queries (lines ~97, 108, 114).
  - Verification: `./gradlew compileJava compileTestJava` — must be green. This is the GREEN step.

- [x] 1.3 Apply the entity-side rename and pin the DB column name.
  - File: `infraestructure/persistence/entity/TransactionEntity.java`
  - Changes: rename field `amount` → `amountCents`; add `@Column(name = "amount")` above the field so the persisted column name stays stable. Lombok auto-regenerates `getAmountCents()` / `setAmountCents(...)`.
  - Verification: `./gradlew compileJava compileTestJava`.

- [x] 1.4 Rename the domain records and update the application DTOs that read them; add wire-contract Javadoc.
  - Files:
    - `domain/TransactionHistoryEntry.java` — rename record component `amount` → `amountCents`.
    - `domain/DashboardAggregate.java` — rename outer `totalAmount` → `totalAmountCents` and inner `CategoryAggregate.totalAmount` → `totalAmountCents`.
    - `application/output/DashboardSummaryResponse.java` — `from()` uses `aggregate.totalAmountCents()`.
    - `application/output/CategoryTotalResponse.java` — `from()` uses `aggregate.totalAmountCents()`.
    - `application/input/PersistTransactionInput.java` — keep record component `amount`; add class-level Javadoc clarifying it is integer centavos (the existing `@ToolParam(description = "Valor del gasto (en centavos)")` is already correct).
    - `infraestructure/http/request/TransactionRequest.java` — add Javadoc on the `amount` component stating integer centavos.
    - `infraestructure/http/response/TransactionResponse.java` — add Javadoc on the `amount` component stating integer centavos.
    - `application/output/TransactionHistoryResponse.java` and `infraestructure/http/response/TransactionHistoryHttpResponse.java` — verify no rename needed (both already use `totalAmountCents` / `totalAmount`).
  - Verification: `./gradlew compileJava compileTestJava`.

## Phase 2: Service bug fix

- [x] 2.1 Fix the `findHistory` `totalAmount` arithmetic so it actually converts centavos to pesos.
  - File: `application/TransactionService.java`
  - Change: `double totalAmount = (double) totalAmountCents;` → `double totalAmount = totalAmountCents / 100.0;` (in the `findHistory` method only).
  - Verification: `./gradlew compileJava`.

- [x] 2.2 Add a focused regression test in `application/TransactionServiceTest.java` that pins the corrected contract.
  - File: `src/test/java/dio/budgeting/application/TransactionServiceTest.java`
  - Test: extend or pair with the existing `shouldComputeHistoryTotalsFromAllRepositoryEntries` fixture (5750 cents) to assert `assertThat(response.totalAmount()).isEqualTo(57.5)` and explicitly `isEqualTo(response.totalAmountCents() / 100.0)`. Add a dedicated `shouldComputeTotalAmountInPesosAsTotalAmountCentsDividedBy100` test if more isolation is desired.
  - Verification: `./gradlew test --tests "*TransactionServiceTest"`.

- [x] 2.3 Update the controller test expectations that depended on the buggy `totalAmount == totalAmountCents` equality (these would otherwise break after T2.1).
  - Files:
    - `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` — line 222 `$.totalAmount.value(2750.0)` → `27.5`. Zero-valued assertions (line 288) stay `0.0`.
    - `src/test/java/dio/budgeting/infraestructure/http/DashboardControllerTest.java` — line 41 `$.totalAmount.value(5750.0)` → `57.5`; line 47 `$.topCategories[0].totalAmount.value(3500.0)` → `35.0`. Zero-valued assertions (line 68) stay `0.0`.
  - Verification: `./gradlew test --tests "*ControllerTest"`.

## Phase 3: AI / interpret endpoint unit unification

- [x] 3.1 Update the interpretation system prompt so the model returns `amount` in integer centavos, and keep the matching test literal in sync.
  - Files:
    - `assistant/TransactionAssistantFacade.java` — extend `INTERPRETATION_SYSTEM_PROMPT` with an explicit instruction, e.g. `"El monto debe devolverse en centavos como un número entero (para 70 pesos devolvé `amount: 7000`; para $123.50 devolvé `amount: 12350`)."` Keep the existing categories list and the rest of the prompt unchanged.
    - `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java` — update the `EXPECTED_INTERPRETATION_PROMPT` constant to the new text so `verify(chatClientBuilder).defaultSystem(...)` keeps passing.
  - Verification: `./gradlew test --tests "*TransactionAssistantFacadeTest"`.

- [x] 3.2 Document the unit on the AI draft record.
  - File: `assistant/TransactionDraft.java`
  - Change: add a class-level Javadoc (or a Javadoc on the `amount` component) stating that `amount` is an integer in centavos, consistent with `PersistTransactionInput`. No signature change.
  - Verification: `./gradlew compileJava`.

- [x] 3.3 Add a deterministic controller test that pins `/transactions/interpret` returning `amount` in centavos.
  - File: `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java`
  - Test: pin `transactionAssistant.interpret("latte and bread")` to return `new TransactionDraft("Coffee and bread", 2300L, Category.COMIDA)`; perform `POST /transactions/interpret`; assert `jsonPath("$.amount").value(2300)` and `jsonPath("$.description").value("Coffee and bread")` and `jsonPath("$.category").value("COMIDA")`. The fixture is already in centavos; the assertion documents the unit.
  - Verification: `./gradlew test --tests "*TransactionControllerTest"`.

## Phase 4: Spec update

- [x] 4.1 Update `openspec/specs/transaction-api/spec.md` to declare the `amount` unit explicitly and preserve the Given/When/Then shape.
  - File: `openspec/specs/transaction-api/spec.md`
  - Changes:
    - Under `## Purpose`, add a global note: every `amount` field on the wire is an integer in centavos; clients divide by `100` to obtain display pesos.
    - In the "Create a transaction successfully" scenario, state that `amount` is integer centavos.
    - In "List all transactions for current user" and "List transactions for a category", state that the listed `amount` values are integer centavos.
    - In "Preserve `/transactions/interpret` behavior during extraction", state that the response `amount` is integer centavos.
  - Verification: re-read the spec for Given/When/Then continuity; no other capability text changes.

## Phase 5: Full validation

- [x] 5.1 Run the full test suite. `./gradlew test`. All tests must pass.
- [x] 5.2 Run the configured smoke commands from `openspec/config.yaml`.
  - `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"`
  - `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"`
  - Both must pass.
- [x] 5.3 Confirm no new Flyway migration file was added. `ls src/main/resources/db/migration/` must show only the pre-existing `V*__*.sql` files; the rule `apply.require_flyway_for_persistence_changes: true` plus "no migration in this change" must hold.
- [x] 5.4 Confirm no commit has been made by `sdd-apply` (the parent orchestrates commits). Report `git status --short` and `git diff --stat` for the orchestrator's review.
- [x] 5.5 Author apply-progress.md with TDD Cycle Evidence. Document the RED → GREEN → TRIANGULATE → REFACTOR cycles for all behavioral changes, including the toOutput reversion that fixed the cross-endpoint wire inconsistency.
