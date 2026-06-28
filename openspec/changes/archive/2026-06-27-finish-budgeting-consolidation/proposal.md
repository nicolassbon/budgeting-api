# Proposal: Finish Budgeting Consolidation

## Intent

Finish the remaining safe consolidation work by moving transaction AI orchestration out of `TransactionController`, reducing transaction DTO mapping duplication only where contracts stay stable, and strengthening focused tests around the extracted transaction assistant flow.

## Scope

### In Scope
- Extract `/transactions/ai` and `/transactions/interpret` orchestration into a cohesive transaction assistant service/facade.
- Preserve existing HTTP JSON fields, Spring AI tool names, prompt path, transcription, and `audio/mp3` response behavior.
- Reduce transaction request/input and output/response mapping duplication only where HTTP and tool schemas remain identical.
- Add focused tests for the extracted flow and current `TransactionService` contract.

### Out of Scope
- Renaming `infraestructure` to `infrastructure`.
- Rewriting broad live OpenAI integration tests or changing env-gated provider behavior.
- Changing endpoint paths: `POST /transactions/ai`, `POST /transactions/interpret`, or `POST /api/sinthesize`.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `transaction-api`: clarify that transaction AI HTTP endpoints remain stable while orchestration moves behind an assistant-focused component.

## Approach

Use the existing `TransactionService` as the transaction tool seam. Introduce a transaction assistant component that owns transcription, prompt loading, `ChatClient.Builder` setup, tool registration, interpretation, and TTS response assembly. Keep `TransactionController` as a thin HTTP adapter. Apply DTO consolidation only after characterization tests prove JSON field names and tool metadata are unchanged.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modified | Delegate AI flow and keep HTTP compatibility. |
| `src/main/java/dio/budgeting/assistant/` | New/Modified | Add transaction assistant orchestration component. |
| `src/main/java/dio/budgeting/application/*` | Modified | Preserve service/tool seam; simplify safe DTO hops. |
| `src/test/java/dio/budgeting/**` | Modified | Add focused characterization and service tests. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| AI flow behavior changes during extraction | Med | Characterize prompt, tool registration, and response assembly before refactor. |
| DTO dedupe changes JSON/tool schema | Med | Guard HTTP fields and Spring AI tool metadata with tests first. |
| Scope expands into package rename or live-AI rewrite | Low | Keep explicit non-goals and verify changed-line budget. |

## Rollback Plan

Revert the extracted assistant component and controller delegation, restore previous DTO mappings, and rerun `./gradlew test` to confirm existing transaction and assistant contracts.

## Dependencies

- Existing `TransactionService` tool methods and `src/main/resources/prompts/system-message.st`.

## Success Criteria

- [ ] `TransactionController` no longer owns transaction AI orchestration details.
- [ ] Existing transaction endpoints, JSON fields, tool names, and prompt path remain compatible.
- [ ] Focused tests cover the extracted flow and transaction service contract.
- [ ] `./gradlew test` passes.
