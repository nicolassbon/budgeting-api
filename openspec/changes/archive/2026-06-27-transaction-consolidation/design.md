# Design: Transaction Consolidation

## Technical Approach

Lock the current transaction HTTP and AI-tool contracts first, then consolidate only the manual transaction application seam. Replace the two thin use-case services with one transaction application service that owns repository orchestration and tool annotations. Keep HTTP request/response DTOs in `infraestructure/http`, keep persistence mapping in `TransactionEntity`, and preserve `POST /transactions`, `GET /transactions/{category}`, and the Spring AI tool names/fields.

No OpenSpec capability spec exists for this internal refactor; behavior is guarded by tests rather than new requirements.

## Architecture Decisions

| Decision | Options / Tradeoff | Choice | Rationale |
|---|---|---|---|
| Application boundary | Keep one class per use case: explicit but pure pass-through. Merge into one service: fewer seams but less “use-case” naming. | Create `dio.budgeting.application.TransactionService`. | Both current use cases only delegate and map; one service improves locality while keeping the application boundary. |
| Mapping ownership | HTTP maps to application input; application maps to tool/output view; persistence maps entity/domain. Moving all mapping to controller would expose persistence/domain details. | Keep mapping at the nearest boundary. | Prevents DTO leakage and keeps stable JSON separate from AI tool output. |
| AI tool compatibility | Delete tool DTOs: smaller, but changes Spring AI schema. Keep input/output records: less deletion, stable schema. | Keep `PersistTransactionInput` and `TransactionOutput` field names. | `@Tool` parameter/output shape is an external contract for `POST /transactions/ai`. |
| Package structure | Rename `infraestructure`: cleaner but broad risky diff. Preserve typo. | No package rename. | Project standard and proposal explicitly defer that refactor. |

## Data Flow

Manual HTTP:

    TransactionController ──request──→ TransactionService ──domain──→ TransactionRepository
            │                              │                              │
            └──TransactionResponse←── TransactionOutput ←── JpaTransactionRepository/Entity

AI tool flow remains routed through the same service methods registered in `defaultTools(...)`:

    /transactions/ai → ChatClient → TransactionService @Tool methods → Repository

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/application/TransactionService.java` | Create | Single transaction application service with `create(PersistTransactionInput)` and `findAllByCategory(Category)`, preserving existing `@Tool` names/descriptions. |
| `src/main/java/dio/budgeting/application/PersistTransactionUseCase.java` | Delete | Replaced by `TransactionService`; no unique behavior today. |
| `src/main/java/dio/budgeting/application/ListTransactionsByCategoryUseCase.java` | Delete | Replaced by `TransactionService`; no unique behavior today. |
| `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` | Modify | Keep record and `@ToolParam` fields; optionally add `from(TransactionRequest)` only if avoiding HTTP-to-application constructor duplication. |
| `src/main/java/dio/budgeting/application/output/TransactionOutput.java` | Modify | Keep `id`, `description`, `category`, `value`; owns domain-to-output conversion. |
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modify | Inject `TransactionService`; manual endpoints map request/output to HTTP response; keep `/transactions/ai` orchestration and prompt loading unchanged. |
| `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` | Modify | Keep JSON fields `description`, `category`, `amount`; remove avoidable pass-through only if mapping moves to controller/service boundary. |
| `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` | Modify | Keep JSON fields `id`, `description`, `category`, `amount`; continue mapping `TransactionOutput.value()` to `amount`. |
| `src/main/java/dio/budgeting/infraestructure/persistence/**` | Preserve/Modify | Keep repository/entity mapping; only update imports if needed. No schema change. |
| `src/test/java/dio/budgeting/**` | Create | Add characterization tests before deleting use cases. |

## Interfaces / Contracts

```java
@Service
public class TransactionService {
    @Tool(name = "persist-transaction", description = "Persiste una nueva transacción financiera")
    public TransactionOutput create(PersistTransactionInput input) { ... }

    @Tool(name = "list-transactions-by-category", description = "Lista transacciones financieras por categoría")
    public List<TransactionOutput> findAllByCategory(Category category) { ... }
}
```

Contracts to lock: HTTP request `description/category/amount`, HTTP response `id/description/category/amount`, AI input `description/amount/category`, AI output `id/description/category/value`, tool names above.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `TransactionService` creates domain objects, delegates repository, returns `TransactionOutput`; list preserves category filtering output mapping. | JUnit 5 + fake/in-memory `TransactionRepository`; no Spring context. |
| Contract | HTTP JSON request/response and status for `POST /transactions` and `GET /transactions/{category}` stay unchanged. | MockMvc characterization test with service mocked; assert exact JSON field names. |
| Contract | AI tool schema remains stable. | Reflection test on `@Tool` names/descriptions, `@ToolParam` input fields, and `TransactionOutput` record components. |
| Integration | Persistence/Flyway unaffected. | Existing `BudgetingApplicationTests` and `FlywayMigrationIT`; full `./gradlew test` before completion. |

## Migration / Rollout

No migration required. No Flyway migration, database field, endpoint path, or response-shape change is planned. Rollout is a code-only refactor protected by characterization tests.

## Open Questions

- [ ] None.
