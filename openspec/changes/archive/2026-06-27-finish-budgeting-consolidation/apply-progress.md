# Apply Progress: finish-budgeting-consolidation

## Provenance

This progress note was reconstructed from the current source tree, the focused/full Gradle test runs in this session, and `verify-report.md`.
The original day-by-day RED/GREEN execution log was not preserved, so chronology below is explicit about what is directly verified now versus what is reconstructed from the surviving artifacts.

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1 | `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Integration / HTTP adapter | Reconstructed | ✅ Present | ✅ Passing | ✅ Endpoint/audio + JSON contract cases | ✅ Clean |
| 1.2 | `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` | Unit | Reconstructed | ✅ Present | ✅ Passing | ✅ Tool metadata + DTO field boundaries | ✅ Clean |
| 1.3 | `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java` | Unit | Reconstructed | ✅ Present | ✅ Passing | ✅ Added prompt-path, `defaultTools(transactionService)`, validation, and upstream-failure assertions without reflective invocation | ✅ Clean |
| 2.1 | `src/main/java/dio/budgeting/assistant/TransactionAssistantFacade.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ Facade owns transcription, chat, TTS, interpretation | ✅ Clean |
| 2.2 | `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ `/transactions/ai` and `/transactions/interpret` delegate to assistant | ✅ Clean |
| 2.3 | `src/main/java/dio/budgeting/assistant/TransactionDraft.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ JSON contract preserved | ✅ Clean |
| 3.1 | `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ Application-owned factory preserved | ✅ Clean |
| 3.2 | `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ Thin HTTP adapter preserved | ✅ Clean |
| 3.3 | `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` | Production | Reconstructed | ✅ Implemented | ✅ Verified | ✅ HTTP `amount` vs tool `value` boundary preserved | ✅ Clean |
| 4.1 | `src/test/java/dio/budgeting/assistant/TransactionAssistantFacadeTest.java`, `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java`, `src/test/java/dio/budgeting/assistant/AssistantDemoControllerTest.java`, `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` | Verification | N/A | ✅ Present | ✅ Focused Gradle suite passed | ➖ Single verification batch | ➖ None needed |
| 4.2 | `BudgetingApplicationTests` | Verification | N/A | ✅ Present | ✅ `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` passed | ➖ Single verification batch | ➖ None needed |
| 4.3 | Full suite | Verification | N/A | ✅ Present | ✅ `./gradlew test` passed | ➖ Single verification batch | ➖ None needed |

## Verification Notes

- Focused facade test run: `./gradlew test --tests "dio.budgeting.assistant.TransactionAssistantFacadeTest"` → **BUILD SUCCESSFUL**
- Full suite run: `./gradlew test` → **BUILD SUCCESSFUL**
- Live OpenAI integration tests remained env-gated; `OPENAI_API_KEY` was not set, so no live-provider run was added.

## Recovery Notes

- The verification blocker was not code failure; it was missing persisted apply-progress evidence.
- The additional facade assertions now prove two missing contracts without touching live OpenAI:
  - `classpath:/prompts/system-message.st` is the injected system prompt source.
  - `TransactionService` is registered through `defaultTools(transactionService)` on the transaction AI client.
- Assistant endpoints now fail with explicit 400/502 JSON responses plus logging for validation and upstream AI errors, while success contracts for `/transactions/ai`, `/transactions/interpret`, and `/api/sinthesize` remain unchanged.
