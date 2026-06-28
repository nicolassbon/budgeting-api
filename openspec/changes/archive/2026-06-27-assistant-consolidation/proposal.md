# Proposal: Assistant Controller Consolidation

## Intent

Move the four root AI demo controllers into a dedicated assistant boundary so AI/demo HTTP adapters stop living in the application root package. This first slice improves locality without changing endpoint behavior, transaction AI orchestration, prompts, persistence, or package spelling.

## Scope

### In Scope
- Move `ChatClientController`, `ChatModelController`, `TranscriptionController`, and `TextToSpeechController` under an assistant package/module boundary.
- Preserve existing `/api/*` endpoint behavior, including the misspelled compatibility endpoint `POST /api/sinthesize`.
- Adjust only immediate wiring/package references needed for the moved controllers.
- Add or update focused characterization coverage if needed to prove endpoint compatibility.

### Out of Scope
- Moving or redesigning `/transactions/ai` orchestration in `TransactionController`.
- Prompt redesign or relocation of `src/main/resources/prompts/system-message.st`.
- Renaming `infraestructure` to `infrastructure`.
- Consolidating transaction CRUD/application logic or changing Spring AI tool contracts.

## Capabilities

### New Capabilities
- `assistant-demo-api`: Stable contract for the root assistant/demo `/api/*` endpoints while they move behind an assistant boundary.

### Modified Capabilities
- None.

## Approach

Use controller-only grouping: relocate the four demo controllers from `dio.budgeting` to a dedicated `dio.budgeting.assistant` boundary, keeping annotations, paths, injected Spring AI collaborators, and response behavior unchanged. Do not touch `TransactionController`, `BudgetingApplication` bean semantics, or the prompt resource.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/*Controller.java` | Modified | Move four root AI demo controllers into assistant boundary. |
| `src/main/java/dio/budgeting/assistant/` | New | Home for assistant/demo HTTP adapters. |
| `src/test/java/dio/budgeting/` | Modified | Add/update compatibility tests only if current coverage is insufficient. |
| `openspec/specs/assistant-demo-api/spec.md` | New | Spec for preserved `/api/*` assistant/demo endpoint contracts. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Accidental endpoint path change | Med | Characterize `/api/*` mappings before/with move. |
| Breaking `/api/sinthesize` typo compatibility | Med | Explicitly test or review the exact mapping. |
| Review budget creep into transaction AI flow | Med | Forbid `TransactionController` and prompt changes in this slice. |

## Rollback Plan

Revert the controller package move and any focused tests/spec deltas. No database, Flyway, prompt, or transaction orchestration migration is involved.

## Dependencies

- Existing Spring component scanning from `dio.budgeting.BudgetingApplication` must continue to include `dio.budgeting.assistant`.

## Success Criteria

- [ ] All four `/api/*` demo endpoints keep their current paths and behavior.
- [ ] `POST /api/sinthesize` remains compatible.
- [ ] `/transactions/ai`, `system-message.st`, and `infraestructure` remain unchanged.
- [ ] Full verification uses `./gradlew test`.
