# Proposal: Unify Amount Unit on Centavos (Backend)

## Intent

Unify the budgeting-backend on a single, explicit amount unit — integer centavos — across the domain model, application DTOs, AI draft endpoint, and OpenSpec spec, and fix a latent `findHistory` `totalAmount` bug. This eliminates the silent drift between `/transactions/interpret` (which returns pesos) and the persisted `POST /transactions` API (which uses centavos), makes the unit impossible to miss in code, and prepares the storage shape for a future `currency` column without re-migrating amounts.

## Proposal question round

Mode is automatic. The parent orchestrator already ran a clarifying question round with the user, who explicitly approved the full "Opción A" scope below for the backend. The following assumptions are baked in for spot-checking; correct any that are wrong before design begins.

1. **Storage unit is unchanged.** The database `amount` column already stores centavos and is not migrated; only the Java field is renamed, and `@Column(name = "amount")` keeps the DB column name stable so no destructive Flyway migration is produced.
2. **The external behavior change is limited to `/transactions/interpret`.** Its `amount` field switches from pesos to integer centavos. All other response shapes keep the same wire field name `amount` (now documented as centavos) so no other contract breaks.
3. **The frontend consumes this change via a coordinated frontend change** (`unify-amount-unit-centavos` in `budgeting-frontend/openspec/changes/`); the two ship together or the backend behavior is withheld until the frontend is ready.
4. **The `persist-transaction` AI tool-call contract is untouched** — it already uses centavos and keeps working.
5. **The `findHistory` `totalAmount` bug fix is silent:** the frontend only reads `totalAmountCents` today, so correcting `totalAmount` to `totalAmountCents / 100.0` changes no current client.
6. **Defensive AI parsing (e.g., multiply-by-100 heuristic if the model still returns pesos) is a follow-up, not part of this change.** This change ships the prompt fix only.
7. **Multi-currency is explicitly out of scope** — the unit unification is preparation only; a `currency` column is a future change.
8. **Amounts remain integer `long`/`Long` centavos; no BigDecimal migration** is introduced by this change.

If any assumption is wrong, raise it before the design phase; otherwise design proceeds on these premises.

## Scope

### In Scope (backend)
1. **Fix the latent `findHistory` bug**: `application/TransactionService.findHistory` computes `totalAmount` (meant to be in pesos) as `(double) totalAmountCents`, which leaves it numerically identical to `totalAmountCents` (still centavos). Change to `totalAmountCents / 100.0`. No current client observes this field today, but the contract currently lies.
2. **Rename `Transaction.amount` → `Transaction.amountCents`** in the domain model, JPA entity (keeping the DB column name `amount` via `@Column`), application services, repositories, inputs, outputs, and tests. The field is already in centavos; the rename only makes the unit explicit in code.
3. **Unify the unit of `/transactions/interpret`** to return `amount` in centavos. Today the AI draft endpoint returns `amount` in pesos (because the AI thinks in pesos) while `POST /transactions` and persistence use centavos. Update `TransactionAssistantFacade.INTERPRETATION_SYSTEM_PROMPT` to instruct the model to return `amount` in integer centavos; document `TransactionDraft.amount` as centavos via Javadoc. The `persist-transaction` tool call already uses centavos and stays unchanged.
4. **Clarify the unit in the OpenSpec spec** `openspec/specs/transaction-api/spec.md`: explicitly state that `amount` in create/list/history responses and in `/transactions/interpret` is integer centavos, and that clients divide by `100` to obtain pesos for display.

### Out of Scope
- Multi-currency support. The unit unification is preparation only; a `currency` column is a future change.
- Frontend changes (handled by the coordinated `unify-amount-unit-centavos` change in `budgeting-frontend`).
- Migration of existing stored amounts or renaming the DB column.
- Changing the `persist-transaction` AI tool-call contract.
- `BigDecimal`/precision migration of the amount type.

## Capabilities

### Modified Capabilities
- `transaction-api`: Clarify the `amount` unit as integer centavos in create, list-by-category, history, and `/transactions/interpret` scenarios, while preserving endpoint paths, tool exposure, auth, and owner-scoping requirements.

No new capabilities are introduced by this change.

## Approach

Rename in Java only — no DB migration, no column rename; `@Column(name = "amount")` preserves the persisted contract. Then tighten the spec, update the AI system prompt so `/transactions/interpret` emits centavos, document `TransactionDraft.amount` as centavos, and fix the `findHistory` `totalAmount` arithmetic. The change is small and mechanical: the only externally visible behavior shift is that `/transactions/interpret` now returns `amount` in centavos instead of pesos, which the coordinated frontend change is ready to consume. Strict TDD plus `./gradlew compileJava compileTestJava` after each rename step catches missed `getAmount()`/`setAmount()` call sites.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/Transaction.java` | Modified | Rename `amount` → `amountCents`. |
| `infraestructure/persistence/entity/TransactionEntity.java` | Modified | Rename field; keep DB column name via `@Column(name = "amount")`. |
| `application/TransactionService.java` | Modified | Rename field usages; fix `findHistory` `totalAmount` = `totalAmountCents / 100.0`. |
| `application/output/TransactionOutput.java` | Modified | Field name follows the domain. |
| `application/output/TransactionHistoryResponse.java` | Modified | Field name; `totalAmount` correctness covered by service fix. |
| `application/output/DashboardSummaryResponse.java` | Modified | Field name. |
| `application/output/CategoryTotalResponse.java` | Modified | Field name. |
| `application/input/PersistTransactionInput.java` | Modified | Field name; keep `@ToolParam` description aligned to "centavos". |
| `assistant/TransactionDraft.java` | Modified | Add Javadoc clarifying `amount` is integer centavos. |
| `assistant/TransactionAssistantFacade.java` | Modified | Update `INTERPRETATION_SYSTEM_PROMPT` to instruct the AI to return `amount` in centavos. |
| `openspec/specs/transaction-api/spec.md` | Modified | Clarify that `amount` is integer centavos in create/list/history and `/interpret` scenarios. |
| Backend tests referencing `getAmount()` / `setAmount()` / record `amount` accessors | Modified | Follow renames. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| AI model returns pesos from `/transactions/interpret` despite the prompt change | Med | Ship the prompt fix now; treat defensively (e.g., multiply by 100 when a value looks like pesos, such as `< 1_000_000`) as a stated follow-up if production traffic shows the drift. Flag in design. |
| Missing a `getAmount()` call site causes compile failure | High | Run `./gradlew compileJava compileTestJava` after each rename step; rely on strict TDD. |
| Frontend out of sync if backend ships first | High | Ship behind a single coordinated release with the frontend `unify-amount-unit-centavos` change; withhold the backend `/interpret` behavior switch until the frontend is ready to consume centavos. |
| Existing Flyway migrations reference the `amount` column | Low | DB column is NOT renamed; `@Column(name = "amount")` keeps the stored contract and avoids a destructive migration. |
| Hidden downcast/precision change from the `findHistory` fix | Low | Integer division now truly yields pesos; document the corrected semantics in the spec and confirm via focused tests. |

## Rollback Plan

Revert the commits. There is no DB migration to undo, and no DB column rename to revert. The AI prompt revert is a single-line change back to the peso-instructing prompt. If the change has shipped ahead of the frontend, roll back the backend prompt and DTO documentation only and re-ship with the coordinated frontend release.

## Dependencies

- Coordinated frontend change `unify-amount-unit-centavos` in `budgeting-frontend/openspec/changes/`. The user has approved shipping backend and frontend changes together.
- No new backend dependencies (Spring AI, Flyway, Hibernate versions unchanged).

## Success Criteria

- [ ] `./gradlew test` passes.
- [ ] `Transaction.amountCents` is the field name across the domain, JPA entity, application inputs/outputs, and services.
- [ ] `findHistory` returns `totalAmount == totalAmountCents / 100.0` (pe pesos), verified by a focused test.
- [ ] `/transactions/interpret` returns `amount` as an integer centavos value per the updated system prompt.
- [ ] `openspec/specs/transaction-api/spec.md` explicitly states that `amount` is integer centavos in create, list-by-category, history, and `/transactions/interpret` scenarios, and that clients divide by `100` for display pesos.
- [ ] DB column name remains `amount`; no new Flyway migration is introduced by this change.
- [ ] `persist-transaction` AI tool-call behavior and contract unchanged.