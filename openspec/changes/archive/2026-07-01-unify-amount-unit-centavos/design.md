# Design: Unify Amount Unit on Centavos

## Technical Approach

Rename the internal amount field to make the centavos unit explicit, keep the database `amount` column untouched via `@Column(name = "amount")`, fix the latent `findHistory` pesos conversion bug, and update the AI interpretation prompt so `/transactions/interpret` returns integer centavos instead of pesos. External wire and AI tool-call field names stay the same (`amount` for HTTP and tool input, `value` for tool output); only the unit semantics and internal Java names change.

## Architecture / Data Flow

Today the backend stores and aggregates money as integer centavos end-to-end, but the code hides the unit behind a bare `amount` name. The domain `Transaction` holds a `long amount`, the JPA `TransactionEntity` maps it to the `amount BIGINT` column, and HTTP/AI payloads expose the same `amount` JSON/tool field. Two places violate that story:

1. `POST /transactions/interpret` returns `TransactionDraft.amount` in pesos because the interpretation prompt asks the model for a numeric monto without specifying centavos.
2. `TransactionService.findHistory` computes `totalAmount` as `(double) totalAmountCents`, so the `totalAmount` field is still centavos even though it is documented/expected as pesos.

The target shape keeps the persistence contract unchanged and makes the unit unavoidable in Java: `Transaction.amountCents`, `TransactionEntity.amountCents` (`@Column(name = "amount")`), `TransactionHistoryEntry.amountCents`, and `DashboardAggregate.totalAmountCents`. Application DTOs that are bound to external contracts keep their existing component names (`PersistTransactionInput.amount`, `TransactionOutput.value`) but gain Javadoc/`@ToolParam` descriptions stating "centavos". The spec explicitly documents that every `amount` field on the wire is integer centavos and that display pesos are obtained by dividing by 100. `findHistory` returns `totalAmount == totalAmountCents / 100.0`, and `/transactions/interpret` returns `amount` in centavos.

## Architecture Decisions

| Topic | Options / Tradeoff | Decision |
|------|---------------------|----------|
| Unit | Pesos (`double`) is human-friendly; centavos (`long`) avoids rounding in aggregation and matches current storage. | Keep centavos as the single unit; only rename and document. |
| DB column rename | Renaming to `amount_cents` would make schema and code consistent, but requires a destructive migration. | Keep column name `amount` via `@Column`; no Flyway change. |
| AI interpretation contract | Keep prompt asking for pesos and convert in code; or ask model for centavos. | Ask model for centavos; simpler, fewer conversions, and matches the rest of the API. |
| `findHistory.totalAmount` | Remove the field; keep it but fix the math; or leave the bug. | Fix the math so `totalAmount` is pesos; no current client reads it, so the correction is safe. |

## Tradeoffs

### A. Unify on centavos, rename Java field, keep DB column (chosen)

Rename internal Java fields to `amountCents` / `totalAmountCents` and keep the persisted `amount` column. This is the chosen path.

- **Pros:** No destructive migration; no Flyway script to write, review, and rollback; the change is reversible by reverting code; centavos remain the lossless aggregation unit; the future `currency` column can be added without touching amounts.
- **Cons:** The DB column name (`amount`) and the Java field name (`amountCents`) diverge slightly, which is mitigated by an explicit `@Column(name = "amount")` annotation.

### B. Unify on pesos, migrate column to `NUMERIC(15,2)`

Convert storage and domain to decimal pesos (`BigDecimal` or `double`).

- **Pros:** Domain matches the unit users see; no need to explain "divide by 100" on every API field.
- **Cons:** Requires a destructive migration that reinterprets every stored centavo value; breaks the precision story for aggregation; forces the AI tool-call contract (`persist-transaction`) to switch from `long` centavos to decimal pesos, which is a wider contract change; floating-point sums are undesirable for a budgeting MVP.

### C. Add a `Money` value object now

Introduce `Money` (or `Amount` + `Currency`) and replace `long` amount fields with it.

- **Pros:** Gives future multi-currency support a clean, type-safe home; removes primitive obsession.
- **Cons:** Over-engineering for the current MVP: there is only one currency, no arithmetic policy disputes, and no serialization contract for a new composite type. A value object can be introduced later without preventing this rename, and deferring it avoids expanding the change surface.

**Justification:** Option A is the smallest reversible step that solves the inconsistency. It preserves the existing precision and storage contract, unblocks the coordinated frontend change, and leaves the door open for Option C once multi-currency is actually in scope.

## Migration Impact

Per `openspec/config.yaml` `document_migration_impact: true`:

- **DB schema:** no migration. The `amount` column name and `BIGINT` type stay the same. Rationale: keeps the change reversible, avoids the historical `infraestructure` typo cascade, and lets us land the rename before any future currency work. `TransactionEntity` adds `@Column(name = "amount")` explicitly to make the stability contract visible.
- **Wire contract:** `POST /transactions/interpret` switches from pesos to centavos. The only consumer is the coordinated frontend change (`budgeting-frontend/openspec/changes/unify-amount-unit-centavos`); there are no external clients today. The spec will be updated to document the new unit.
- **AI tool-call contract:** `persist-transaction` already uses centavos; no change. Tool parameter name remains `amount`, tool output record component remains `value`; only descriptions/Javadoc are tightened.
- **Tests:** test fixtures using `.amount(12345)` in cents stay valid because the field semantics are unchanged; only the internal accessor names change. No new Flyway migration file is introduced.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `domain/Transaction.java` | Modify | Rename field `amount` → `amountCents`; update constructors and `getAmountCents()`. |
| `domain/TransactionHistoryEntry.java` | Modify | Rename record component `amount` → `amountCents`. |
| `domain/DashboardAggregate.java` | Modify | Rename `totalAmount` → `totalAmountCents` in aggregate and category aggregate. |
| `infraestructure/persistence/entity/TransactionEntity.java` | Modify | Rename field `amount` → `amountCents`; add `@Column(name = "amount")`; update mappers. |
| `infraestructure/persistence/repository/TransactionEntityRepository.java` | Modify | Update JPQL references from `t.amount` to `t.amountCents`; aliases stay descriptive. |
| `application/TransactionService.java` | Modify | Use `getAmountCents()` / `amountCents()`; fix `findHistory` to `totalAmountCents / 100.0`. |
| `application/output/TransactionOutput.java` | Modify | Keep record component `value` (tool output contract); update `from()` to call `getAmountCents()`; add Javadoc that `value` is centavos. |
| `application/output/TransactionHistoryResponse.java` | Modify | No component rename needed (already `totalAmountCents`); verify `totalAmount` is computed in service. |
| `application/output/DashboardSummaryResponse.java` | Modify | Update `from()` to call `aggregate.totalAmountCents()`; keep existing response fields. |
| `application/output/CategoryTotalResponse.java` | Modify | Update `from()` to call `aggregate.totalAmountCents()`; keep existing response fields. |
| `application/input/PersistTransactionInput.java` | Modify | Keep record component `amount` (tool input contract); update `@ToolParam` description if needed; add class/field Javadoc stating centavos. |
| `assistant/TransactionDraft.java` | Modify | Add Javadoc clarifying `amount` is integer centavos. |
| `assistant/TransactionAssistantFacade.java` | Modify | Update `INTERPRETATION_SYSTEM_PROMPT` to instruct the model to return `amount` in integer centavos. |
| `infraestructure/http/response/TransactionResponse.java` | Modify | Keep `amount` wire field; add Javadoc that it is centavos. |
| `infraestructure/http/response/TransactionHistoryHttpResponse.java` | Modify | No field rename; keep passthrough. |
| `openspec/specs/transaction-api/spec.md` | Modify | Explicitly state `amount` is integer centavos in create, list-by-category, history, and `/transactions/interpret` scenarios; note display pesos = `amount / 100`. |
| `src/test/java/...` | Modify | Update accessors to `getAmountCents()` / `setAmountCents()` / `.amountCents()`; add `findHistory.totalAmount` test; add `/transactions/interpret` centavos test. |

## Interfaces / Contracts

```java
// Domain
public class Transaction {
    private long amountCents;
    // ...
}

public record TransactionHistoryEntry(
        TransactionId id,
        String description,
        long amountCents,
        Category category,
        Instant occurredAt
) {}

public record DashboardAggregate(
        long totalAmountCents,
        long transactionCount,
        List<CategoryAggregate> categoryAggregates
) {
    public record CategoryAggregate(Category category, long totalAmountCents, long transactionCount) {}
}

// JPA
@Entity
public class TransactionEntity {
    @Column(name = "amount")
    private long amountCents;
    // ...
}

// AI / HTTP contracts (stable field names, clarified semantics)
public record TransactionDraft(String description, Long amount, Category category) { }
public record TransactionOutput(String id, String description, String category, double value, Instant date) { }
public record TransactionResponse(String id, String description, String category, double amount, Instant date) { }
public record PersistTransactionInput(
        @ToolParam(description = "Descripción del gasto") String description,
        @ToolParam(description = "Valor del gasto (en centavos)") long amount,
        @ToolParam(description = "Categoría de una transacción") Category category,
        @ToolParam(description = "Fecha y hora opcional de la transacción en formato ISO-8601 (UTC)") Instant occurredAt
) { }
```

## File-by-File Change Plan

Recommended order to keep the build green:

1. **Domain rename:** `Transaction.java`, `TransactionHistoryEntry.java`, `DashboardAggregate.java`.
2. **Persistence propagation:** `TransactionEntity.java` (field + `@Column` + mappers), `TransactionEntityRepository.java` (JPQL).
3. **Compile checkpoint:** `./gradlew compileJava compileTestJava`. Fix any missed `getAmount()` / `.amount()` call sites.
4. **Application layer:** `TransactionService.java` (constructors, `findHistory` fix), `TransactionOutput.java`, `DashboardSummaryResponse.java`, `CategoryTotalResponse.java`, `PersistTransactionInput.java`.
5. **Assistant layer:** `TransactionAssistantFacade.java` prompt update, `TransactionDraft.java` Javadoc.
6. **HTTP layer:** `TransactionResponse.java` Javadoc, `TransactionHistoryHttpResponse.java` (verify unchanged).
7. **Spec update:** `openspec/specs/transaction-api/spec.md`.
8. **Bug fix verification:** ensure `findHistory` returns `totalAmount == totalAmountCents / 100.0`.
9. **Test update and run:** `./gradlew test`.

Concrete diff shape per file:

- `domain/Transaction.java`: rename field, all constructor parameters, and getter to `amountCents`.
- `domain/TransactionHistoryEntry.java`: rename record component to `amountCents`.
- `domain/DashboardAggregate.java`: rename both `totalAmount` record components to `totalAmountCents`.
- `infraestructure/persistence/entity/TransactionEntity.java`: rename field to `amountCents`, annotate `@Column(name = "amount")`, update `from()`/`toDomain()`/`toHistoryEntry()`.
- `infraestructure/persistence/repository/TransactionEntityRepository.java`: change JPQL property path from `t.amount` to `t.amountCents` in sum/count queries.
- `application/TransactionService.java`: keep `input.amount()` because the tool input contract is unchanged, replace `transaction.getAmount()` with `transaction.getAmountCents()`, `TransactionHistoryEntry::amount` with `TransactionHistoryEntry::amountCents`, `entry.amount()` with `entry.amountCents()`, and change `double totalAmount = (double) totalAmountCents;` to `double totalAmount = totalAmountCents / 100.0;`.
- `application/output/TransactionOutput.java`: update `from()` to use `transaction.getAmountCents()`; add Javadoc on `value`.
- `application/output/DashboardSummaryResponse.java` / `CategoryTotalResponse.java`: use `aggregate.totalAmountCents()` / `categoryAggregate.totalAmountCents()`.
- `application/input/PersistTransactionInput.java`: add class/field Javadoc; keep `@ToolParam` description as-is (already says "en centavos").
- `assistant/TransactionDraft.java`: add class-level Javadoc and component Javadoc stating `amount` is integer centavos.
- `assistant/TransactionAssistantFacade.java`: append to `INTERPRETATION_SYSTEM_PROMPT` an explicit instruction: "El monto debe devolverse en centavos como un número entero (por ejemplo, 12350 para $123.50)."
- `openspec/specs/transaction-api/spec.md`: add a note under each `amount` scenario that values are integer centavos; add a global note that display pesos = `amount / 100`.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `findHistory` `totalAmount` is pesos | `TransactionServiceTest`: assert `response.totalAmount()` equals `totalAmountCents / 100.0` (e.g., 5750 cents → 57.50). |
| Unit | Renamed accessors compile and behave identically | Update `TransactionServiceTest` and fakes to use `getAmountCents()` / `setAmountCents()` / `.amountCents()`; values stay the same. |
| Contract | AI tool schema unchanged | `TransactionToolContractTest` keeps `amount` input param and `value` output component; description still mentions centavos. |
| Web | `/transactions/interpret` returns centavos | `TransactionControllerTest` mocks `transactionAssistant.interpret(...)` returning a draft with a centavos value and asserts `$.amount` matches. |
| Web | Manual HTTP endpoints still accept/return centavos | Update existing controller tests only where internal field accessors changed; wire JSON remains unchanged. |
| Integration | No schema drift | `FlywayMigrationIT` and `BudgetingApplicationTests` pass; confirm no new `V*__*.sql` file is added. |
| Regression | Full suite | `./gradlew test` passes. |

## Verification

Run the configured verification command after every checkpoint and at completion:

```bash
./gradlew test
```

Specific smoke commands from `openspec/config.yaml`:

```bash
./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"
./gradlew test --tests "dio.budgeting.FlywayMigrationIT"
```

No new Flyway migration file should appear in `src/main/resources/db/migration/`.

## Migration / Rollout

No phased rollout is required. The change is code-only and reversible. Coordinate the backend release with the frontend `unify-amount-unit-centavos` change so that `/transactions/interpret` switches to centavos on both sides at the same time. If the backend must ship first, withhold only the prompt change (and therefore the `/interpret` centavos behavior) until the frontend is ready.

## Risks and Follow-ups

Carried over from the proposal:

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| AI model returns pesos from `/transactions/interpret` despite prompt change | Med | Ship the prompt fix now; add a defensive parse follow-up if production traffic shows drift. |
| Missing a `getAmount()` call site causes compile failure | High | Run `./gradlew compileJava compileTestJava` after each rename step; rely on strict TDD. |
| Frontend out of sync if backend ships first | High | Ship with the coordinated frontend change, or withhold the `/interpret` prompt change until the frontend is ready. |
| Existing Flyway migrations reference the `amount` column | Low | DB column is not renamed; `@Column(name = "amount")` keeps the stored contract. |
| Hidden downcast/precision change from `findHistory` fix | Low | Integer division now truly yields pesos; document corrected semantics and cover with focused tests. |

Additional follow-ups:

- **Defensive parse follow-up:** If production traffic shows the AI still returning pesos from `/transactions/interpret`, the next change should multiply the returned value by 100 when it looks like a peso value (e.g., `< 1_000_000` or otherwise fails a centavos sanity check). This is intentionally deferred from the current change.
- **Future `Money` value object:** When multi-currency becomes a requirement, introduce a `Money` (or `Amount` + `Currency`) value object and migrate this rename into it. The current rename keeps that future refactor smaller by already making the unit explicit.

## Open Questions

- [ ] None.
