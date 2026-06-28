# Design: Assistant Controller Consolidation

## Technical Approach

Relocate only the four root AI/demo HTTP adapters from `dio.budgeting` to `dio.budgeting.assistant`. Keep their class names, Spring MVC annotations, endpoint paths, injected Spring AI collaborators, request binding, response types, and method behavior unchanged. `BudgetingApplication` remains in `dio.budgeting`, so default component scanning still includes the new child package. `/transactions/ai`, `src/main/resources/prompts/system-message.st`, persistence, Flyway, and the `infraestructure` typo stay untouched.

## Architecture Decisions

| Decision | Choice | Alternatives considered | Rationale |
|----------|--------|--------------------------|-----------|
| Assistant boundary shape | Use `dio.budgeting.assistant` for the four demo controllers. | `dio.budgeting.infraestructure.http.assistant`; rename broader AI flow packages. | The proposal asks for a narrow assistant-only boundary. A root child package is scanned automatically and avoids mixing demo AI endpoints with transaction HTTP adapters. |
| Controller behavior | Move files without extracting services or changing annotations. | Introduce assistant services/facades now. | This slice is a locality refactor. Extracting services would add behavior risk and review noise without improving the stated boundary. |
| Supporting configuration | Do not move `BudgetingApplication#chatClient` or prompt configuration. | Create assistant config class. | The `ChatClient` bean is used by current root demo behavior and may be shared by future slices. No new configuration is needed for scanning or compatibility. |
| Tests | Add focused HTTP characterization tests with mocked Spring AI collaborators. | Rely on env-gated live OpenAI ITs only. | Strict TDD needs deterministic proof that mappings and response contracts survive relocation; live tests are gated and not suitable as the only contract proof. |

## Data Flow

No runtime flow changes.

```text
HTTP /api/* request
    -> dio.budgeting.assistant.*Controller
    -> existing Spring AI model/client bean
    -> same text/audio response shape
```

`/transactions/ai` remains:

```text
HTTP /transactions/ai -> infraestructure.http.TransactionController -> transcription -> ChatClient tools -> TTS
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/assistant/ChatClientController.java` | Create/move | Same `GET /api/chat-client` adapter; update package only. |
| `src/main/java/dio/budgeting/assistant/ChatModelController.java` | Create/move | Same `GET /api/chat-model` adapter; update package only. |
| `src/main/java/dio/budgeting/assistant/TranscriptionController.java` | Create/move | Same `POST /api/transcribe` multipart adapter; update package only. |
| `src/main/java/dio/budgeting/assistant/TextToSpeechController.java` | Create/move | Same `POST /api/sinthesize` compatibility adapter; update package only. |
| `src/main/java/dio/budgeting/{ChatClientController,ChatModelController,TranscriptionController,TextToSpeechController}.java` | Delete/move source | Remove root-package copies after relocation. |
| `src/test/java/dio/budgeting/assistant/AssistantDemoControllerTest.java` | Create | Deterministic MockMvc compatibility tests for all four `/api/*` endpoints. |

## Interfaces / Contracts

No new public Java interfaces. HTTP contracts remain:

```text
GET  /api/chat-client?prompt=...
GET  /api/chat-model?prompt=...
POST /api/transcribe multipart/form-data part=file
POST /api/sinthesize JSON { "text": "..." } -> audio/mp3, attachment audio.mp3
```

The misspelled `/api/sinthesize` route is intentionally preserved.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit/HTTP slice | Each moved controller keeps the same route, request binding, media type, and response body/header behavior. | Create `AssistantDemoControllerTest` with MockMvc and Mockito mocks/stubs for `ChatClient`, `OpenAiChatModel`, `TranscriptionModel`, and `TextToSpeechModel`. |
| Integration smoke | Application scanning still discovers controllers under `dio.budgeting.assistant`. | Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` when `OPENAI_API_KEY` is available; otherwise rely on deterministic controller tests plus full suite behavior. |
| Full suite | No regression in transactions, Flyway, or existing env-gated AI ITs. | Run `./gradlew test`; gated OpenAI tests execute only when `OPENAI_API_KEY` is present. |

## Migration / Rollout

No migration required. This is a package/file relocation only; no database, endpoint, prompt, or configuration rollout is needed.

## Open Questions

None.
