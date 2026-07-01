# Apply Progress: harden-ai-interpretation

## Summary

Implemented the approved hardening for `POST /transactions/interpret` on the current `infraestructure.ai` / `infraestructure.http.assistant` package layout.

## Implemented changes

- Added internal interpretation result model:
  - `InterpretationStatus`
  - `InterpretationResult`
- Changed `TransactionAssistant.interpret(...)` to return `InterpretationResult`.
- Added HTTP response DTO `InterpretResponse` with additive top-level `status`.
- Added assistant exceptions:
  - `AssistantSemanticRejectionException`
  - `AssistantRateLimitedException`
  - `AssistantTimeoutException`
- Extended `AssistantExceptionHandler` with:
  - `422 assistant_out_of_scope`
  - `429 assistant_rate_limited` + `Retry-After`
  - `502 assistant_timeout`
- Hardened `AssistantInputValidator.validatePrompt(...)` with:
  - minimum prompt length
  - obvious instruction-override marker rejection
- Added config-backed interpretation properties in `InterpretProperties`:
  - `ai.interpret.rate-limit.requests-per-minute`
  - `ai.interpret.timeout`
  - `ai.interpret.min-prompt-length`
- Added `AiInterpretRateLimiter`, `RateLimitDecision`, and `InMemoryAiInterpretRateLimiter`.
- Updated `TransactionController` to:
  - validate and rate-limit `/transactions/interpret`
  - return `InterpretResponse`
  - emit `RateLimit-*` headers on successful interpretation
  - map `OUT_OF_SCOPE` to semantic rejection
  - log validation/rate-limit/out-of-scope attempts without raw prompt text
- Updated `TransactionAssistantFacade` to:
  - use the hardened interpretation system prompt
  - classify `OK` vs `INCOMPLETE`
  - enforce timeout around the interpretation chat call
  - emit safe telemetry with prompt length + truncated hash + latency + outcome
- Updated tests to cover the new behavior on the current package layout.
- Updated the active OpenSpec hardening artifacts to reflect the current package/test paths after the assistant/test refactors.

## Files changed

### Production
- `src/main/java/dio/budgeting/BudgetingApplication.java`
- `src/main/java/dio/budgeting/config/InterpretProperties.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AssistantInputValidator.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AssistantRateLimitedException.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AssistantSemanticRejectionException.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AssistantTimeoutException.java`
- `src/main/java/dio/budgeting/infraestructure/ai/AiInterpretRateLimiter.java`
- `src/main/java/dio/budgeting/infraestructure/ai/InMemoryAiInterpretRateLimiter.java`
- `src/main/java/dio/budgeting/infraestructure/ai/InterpretationResult.java`
- `src/main/java/dio/budgeting/infraestructure/ai/InterpretationStatus.java`
- `src/main/java/dio/budgeting/infraestructure/ai/RateLimitDecision.java`
- `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistant.java`
- `src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java`
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java`
- `src/main/java/dio/budgeting/infraestructure/http/assistant/AssistantExceptionHandler.java`
- `src/main/java/dio/budgeting/infraestructure/http/response/InterpretResponse.java`
- `src/main/resources/application.properties`

### Tests
- `src/test/java/dio/budgeting/infraestructure/ai/AiInterpretRateLimiterTest.java`
- `src/test/java/dio/budgeting/infraestructure/ai/AssistantInputValidatorTest.java`
- `src/test/java/dio/budgeting/infraestructure/ai/InterpretationResultTest.java`
- `src/test/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacadeTest.java`
- `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java`
- `src/test/java/dio/budgeting/infraestructure/http/SecurityEndpointIntegrationTest.java`

## TDD Cycle Evidence

| Work area | RED evidence added first | GREEN implementation that followed | Safety-net / triangulation |
|---|---|---|---|
| Interpretation result/status contract | `InterpretationResultTest` now pins `OK`, `INCOMPLETE`, `OUT_OF_SCOPE`, and null-status rejection. | Added `InterpretationStatus`, `InterpretationResult`, and internal `InterpretationPayload` mapping path. | Controller tests assert top-level `status` while preserving `description/amount/category`. |
| Prompt hardening | `AssistantInputValidatorTest` covers short prompt, injection-marker rejection, case-insensitivity, and configurable minimum length. | Hardened `AssistantInputValidator` and wired configurable min length through controller/facade for `/transactions/interpret`. | `TransactionControllerTest` proves validation happens before rate-limit consumption. |
| Out-of-scope contract | `TransactionControllerTest` covers `422 assistant_out_of_scope`; `TransactionAssistantFacadeTest` now proves the real facade maps model `status=OUT_OF_SCOPE` to `InterpretationStatus.OUT_OF_SCOPE`. | Added semantic rejection exception/handler path and internal payload parsing for real AI output. | Security integration test keeps authenticated happy-path JSON contract pinned. |
| Rate limiting | `AiInterpretRateLimiterTest` covers allow/deny/isolation; `TransactionControllerTest` covers `429`, `Retry-After`, success headers, and no assistant call on deny. | Added `AiInterpretRateLimiter`, `RateLimitDecision`, and `InMemoryAiInterpretRateLimiter`; wired controller/session checks and headers. | Security integration test preserves anonymous `401` behavior. |
| Timeout and sanitized integration failures | `TransactionAssistantFacadeTest` covers timeout wrapping and generic integration wrapping; `TransactionControllerTest` covers `assistant_timeout`/`assistant_integration_error` response mapping. | Added `AssistantTimeoutException`, timeout boundary around interpretation calls, and sanitized handler responses. | Full Gradle suite plus smoke tests passed after implementation. |
| Log hygiene | `TransactionAssistantFacadeTest` captures logs and asserts presence of prompt length/hash/latency/outcome fields while proving raw prompt text is absent. | Implemented safe telemetry logging in facade/controller paths using truncated SHA-256 and no prompt body logging. | Focused AI/controller tests and full suite exercised the logging path without leaking prompt text. |

## Validation

Passed:
- `./gradlew test --tests "dio.budgeting.infraestructure.ai.*" --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.infraestructure.http.SecurityEndpointIntegrationTest"`
- `./gradlew test` (passed after one retry; see note below)
- `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` (passed after one retry; see note below)
- `./gradlew test --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"` (passed after one retry; see note below)

Note:
- Some individual Gradle test invocations intermittently hit a repo-local runner race: `java.nio.file.NoSuchFileException` under `build/test-results/test/binary/in-progress-results-*.bin`. This occurred once for the full suite, once for `BudgetingApplicationTests`, and once for `infraestructure.persistence.FlywayMigrationIT`. In each case, immediate retry passed without code changes, matching the known transient Gradle cleanup issue already seen in this repo.

## Known follow-ups / deferred items

- Role-based AI authorization remains out of scope.
- In-memory rate limiting is process-local and not distributed.
- Root `/api/*` assistant demo endpoints were not broadened or refactored as part of this change.
- `ArchitectureDocumentationGuidanceTest` had already been deleted in a prior tests-structure refactor and was not reintroduced.
