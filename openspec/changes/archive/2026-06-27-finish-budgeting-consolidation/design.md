# Design: Finish Budgeting Consolidation

## Technical Approach

Keep `TransactionController` as an HTTP adapter and extract the `/transactions/ai` and `/transactions/interpret` flow into an assistant-focused application component. The new component will own prompt loading, `ChatClient.Builder` configuration, transcription, structured interpretation, and MP3 response assembly while continuing to use `TransactionService` as the Spring AI tool host. DTO consolidation stays contract-first: only collapse types whose field names and Spring AI metadata already match.

## Architecture Decisions

| Decision | Options | Choice | Rationale |
|---|---|---|---|
| AI boundary | Keep orchestration in controller; move to `TransactionService`; add assistant facade | Add a dedicated transaction assistant facade/component | Keeps controller thin, avoids mixing HTTP with AI wiring, and preserves `TransactionService` as the transaction/tool seam instead of turning it into a chat orchestration service. |
| Chat client wiring | Reuse generic `ChatClient` bean; build clients inside controller; build assistant-local clients from `ChatClient.Builder` | Build assistant-local clients inside the new component | `BudgetingApplication` exposes a generic bean for demo endpoints only. The transaction flow needs distinct system prompts and tool registration, so local builder-based construction preserves current behavior with less coupling. |
| DTO consolidation | Merge request/input and response/output aggressively; keep all four types | Collapse only same-shape pairs behind stable factory methods/aliases | `TransactionRequest` ↔ `PersistTransactionInput` share the same shape, but `TransactionOutput.value` and `TransactionResponse.amount` intentionally differ for tool vs HTTP contracts. Full merge would break either JSON or tool schemas. |

## Data Flow

`POST /transactions/ai`

    Multipart file
      → TransactionController
      → TransactionAssistantFacade
      → TranscriptionModel.transcribe(file)
      → ChatClient(defaultSystem + defaultTools(TransactionService))
      → TextToSpeechModel.call(result)
      → ResponseEntity<Resource>(audio/mp3)

`POST /transactions/interpret`

    JSON prompt
      → TransactionController
      → TransactionAssistantFacade
      → interpretation ChatClient
      → TransactionDraft JSON

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/assistant/TransactionAssistantFacade.java` | Create | Own AI orchestration for transcription, interpretation, tool-enabled chat, and MP3 response assembly. |
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modify | Delegate AI endpoints to the facade and keep CRUD adapter behavior unchanged. |
| `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` | Modify | Remove direct HTTP dependency by making application-owned construction the primary path. |
| `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` | Modify | Keep HTTP contract stable while delegating to the application-owned input factory. |
| `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` | Modify | Preserve `amount` JSON naming while remaining a thin HTTP mapper from application output. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Modify | Add endpoint characterization for `/transactions/ai` and `/transactions/interpret`. |
| `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java` | Create | Verify orchestration sequence and response assembly without full Spring wiring. |
| `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` | Modify | Keep tool metadata and DTO record-field assertions protecting consolidation limits. |

## Interfaces / Contracts

```java
public interface TransactionAssistant {
    ResponseEntity<Resource> transcribe(MultipartFile file);
    TransactionDraft interpret(String prompt);
}
```

`TransactionDraft` remains the `/transactions/interpret` response contract. `TransactionOutput` keeps `value`; `TransactionResponse` keeps `amount`.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | Facade builds both chat flows correctly | Mockito for `TranscriptionModel`, `ChatClient`, `TextToSpeechModel`, and tool-enabled prompt sequence. |
| Unit | `TransactionService` tool and mapping contract | Keep focused repository-backed unit tests plus tool metadata assertions. |
| Integration | HTTP compatibility for CRUD + AI endpoints | Extend standalone `TransactionControllerTest` with multipart/audio and interpret JSON assertions. |
| Integration | App wiring safety | Keep `BudgetingApplicationTests`; run `ToolCallingIT` only when `OPENAI_API_KEY` is present. |

## Migration / Rollout

No migration required. No Flyway changes, no endpoint renames, and no package rename beyond adding the new assistant component.

## Open Questions

- [ ] Should `TransactionDraft` stay nested in `TransactionController` for compatibility, or move to the assistant package with the controller re-exporting the same JSON shape?
