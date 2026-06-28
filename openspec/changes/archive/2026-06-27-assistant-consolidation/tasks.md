# Tasks: Assistant Controller Consolidation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 180-280 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Characterize the four `/api/*` assistant endpoints before moving code | PR 1 | Tests first; proves paths, media types, and headers |
| 2 | Relocate the four controllers into `dio.budgeting.assistant` | PR 1 | Same PR; no `/transactions/ai`, prompt, or typo-package changes |

## Phase 1: Contract Characterization

- [x] 1.1 Create `src/test/java/dio/budgeting/assistant/AssistantDemoControllerTest.java` with failing MockMvc coverage for `GET /api/chat-client` and `GET /api/chat-model` request/response contracts.
- [x] 1.2 Extend `AssistantDemoControllerTest` with failing multipart coverage for `POST /api/transcribe`, asserting `file` binding and `multipart/form-data` compatibility.
- [x] 1.3 Extend `AssistantDemoControllerTest` with failing JSON/audio coverage for `POST /api/sinthesize`, asserting HTTP 200, `audio/mp3`, and `Content-Disposition: attachment; filename="audio.mp3"`.

## Phase 2: Assistant Package Relocation

- [x] 2.1 Move `src/main/java/dio/budgeting/ChatClientController.java` to `src/main/java/dio/budgeting/assistant/ChatClientController.java` and update only the package declaration/imports.
- [x] 2.2 Move `src/main/java/dio/budgeting/ChatModelController.java` and `src/main/java/dio/budgeting/TranscriptionController.java` into `src/main/java/dio/budgeting/assistant/` without changing mappings or method behavior.
- [x] 2.3 Move `src/main/java/dio/budgeting/TextToSpeechController.java` into `src/main/java/dio/budgeting/assistant/`, preserving `/api/sinthesize`, `audio/mp3`, and the nested request record.

## Phase 3: Focused Verification

- [x] 3.1 Make `AssistantDemoControllerTest` pass using Mockito stubs for `ChatClient`, `OpenAiChatModel`, `TranscriptionModel`, and `TextToSpeechModel`; keep `/transactions/ai` out of the fixture.
- [x] 3.2 Run `./gradlew test --tests "dio.budgeting.assistant.AssistantDemoControllerTest"` as the first GREEN checkpoint for the relocation slice.
- [x] 3.3 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` to verify component scanning still discovers `dio.budgeting.assistant` from the root package.

## Phase 4: Regression Guard

- [x] 4.1 Run `./gradlew test` and confirm no transaction, prompt, or `infraestructure` package changes were introduced by the relocation.
- [x] 4.2 Review the final diff to keep the slice controller-only; if the diff approaches 400 lines, stop and re-plan before apply.
