# Apply Progress: Layered Architecture Adaptation

## Status

- Mode: Strict TDD
- Progress: 12/12 tasks complete
- Delivery: single PR within the approved 800-line review budget

## Completed Tasks

- [x] 1.1 Rewrite `README.md` architecture section to name pragmatic Layered Architecture as the official MVP style.
- [x] 1.2 Add explicit non-goals to `README.md`: strict Hexagonal and full Clean Architecture are out of scope for the MVP.
- [x] 1.3 Update `README.md` layer descriptions to match `domain`, `application`, `infraestructure`, `assistant`, and `config` responsibilities.
- [x] 1.4 Align `openspec/config.yaml` `context` field with the layered architecture decision and PRD-driven MVP rationale.
- [x] 1.5 Review the architecture-guidance spec for final wording consistency with `design.md`; after archive, the active spec lives at `openspec/specs/architecture-guidance/spec.md`.
- [x] 2.1 Audit Java source for comments, class names, or annotations that imply Clean/Hexagonal ownership contrary to the layered decision.
- [x] 2.2 Add or update Javadoc on `assistant/TransactionAssistantFacade.java` to state it is an infrastructure-owned AI orchestrator, not a domain service.
- [x] 2.3 Verify `domain` package contains no Spring, JPA, Security, or Spring AI imports; document any exception found.
- [x] 2.4 Confirm `infraestructure` package spelling remains unchanged and compatibility is preserved.
- [x] 3.1 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` to confirm no startup/regression.
- [x] 3.2 Run `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` to confirm no schema/migration drift.
- [x] 3.3 Grep the repo for leftover "clean architecture", "hexagonal", or "ports and adapters" claims in docs and source comments.

## Files Changed

| File | Action | Notes |
|---|---|---|
| `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Created | Added and later expanded TDD regression coverage for documentation wording, source boundaries, thin-controller guidance, façade ownership, and confirmation-rule preservation. |
| `README.md` | Modified | Replaced outdated Clean/Hexagonal framing with layered guidance, non-goals, responsibilities, and clarified the `/transactions/ai` vs `/transactions/interpret` documentation contract. |
| `openspec/config.yaml` | Modified | Updated architecture context and MVP rationale to match the layered decision. |
| `openspec/specs/architecture-guidance/spec.md` | Created during archive | Clarifies that `assistant` and `config` are infrastructure-owned edges in the current layout. |
| `src/main/java/dio/budgeting/assistant/TransactionAssistantFacade.java` | Modified | Added minimal Javadoc clarifying infrastructure ownership and confirmation-before-save constraint. |
| `openspec/changes/archive/2026-06-29-layered-architecture-adaptation/tasks.md` | Archived | Records all apply tasks complete. |

## TDD Cycle Evidence

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| 1.1 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (docs had no targeted baseline tests) | ✅ Written first against missing layered wording | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ README wording + legacy wording absence | ➖ None needed |
| 1.2 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (docs had no targeted baseline tests) | ✅ Written first against missing MVP non-goal wording | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Official style + non-goal statement | ➖ None needed |
| 1.3 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (docs had no targeted baseline tests) | ✅ Written first against missing layer responsibilities | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Thin-controller guidance + multiple layer assertions + manual fallback assertion | ➖ None needed |
| 1.4 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (docs had no targeted baseline tests) | ✅ Written first against outdated OpenSpec context | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Architecture line + MVP rationale line | ➖ None needed |
| 1.5 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (docs had no targeted baseline tests) | ✅ Written first against incomplete spec alignment | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Spec guidance + future-refactor rule + compatibility constraint | ➖ None needed |
| 2.1 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (audit task) | ✅ Written first against missing regression audit | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ README/config/spec/source checks cover multiple drift paths | ➖ None needed |
| 2.2 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | ✅ `./gradlew test --tests "dio.budgeting.assistant.TransactionAssistantFacadeTest"` | ✅ Written first against missing façade ownership Javadoc | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Ownership wording + confirmation-before-save wording | ➖ None needed |
| 2.3 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (audit task) | ✅ Written first against possible forbidden imports | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Spring/JPA/Security/Spring AI import checks across domain files | ➖ None needed |
| 2.4 | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | N/A (compatibility audit) | ✅ Written first against missing compatibility wording | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ README + spec keep `infraestructure` constraint visible | ➖ None needed |
| 3.1 | `dio.budgeting.BudgetingApplicationTests` | Integration | N/A (verification task) | ➖ Verification task | ✅ `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` | ➖ Verification task | ➖ None needed |
| 3.2 | `dio.budgeting.FlywayMigrationIT` | Integration | N/A (verification task) | ➖ Verification task | ✅ `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` | ➖ Verification task | ➖ None needed |
| 3.3 | `grep: clean architecture|hexagonal|ports and adapters` | Audit | N/A (verification task) | ➖ Verification task | ✅ Repo grep executed; only active change artifacts/test references remain | ➖ Verification task | ➖ None needed |

## Test Summary

- **Total tests written**: 6
- **Total tests passing**: 6
- **Layers used**: Unit (6), Integration (2 commands), E2E (0)
- **Approval tests** (refactoring): None — no behavioral refactor tasks
- **Pure functions created**: 0
- **Additional passing checks**:
  - `./gradlew test --tests "dio.budgeting.assistant.TransactionAssistantFacadeTest"`
  - `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"`
  - `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"`

## Notes

- No behavior or persistence contract changed.
- Follow-up documentation now states that `/transactions/ai` preserves the current transcription + tool-calling + audio flow, while `/transactions/interpret` is the explicit draft/confirmation path.
- The grep audit still finds historical references inside archived OpenSpec change artifacts and the regression test; no leftover claims remain in official runtime docs or source comments.

## Continuation: Verification Gap Fixes

### Completed Follow-up Items

- [x] Added explicit passing coverage for the thin-controller transport guidance scenario.
- [x] Added explicit passing coverage for the mandatory confirmation-before-save refactor rule.
- [x] Clarified the README endpoint wording so `/transactions/ai` is no longer described as a confirmation-gated draft flow.

### Focused TDD Evidence

| Follow-up item | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|---|---|---|---|---|---|---|---|
| Thin-controller transport guidance | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Added failing README assertion for thin-controller/no-business-rule wording | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Paired with existing layer-responsibility/manual-flow assertions | ➖ None needed |
| Confirmation-before-save refactor rule | `src/test/java/dio/budgeting/ArchitectureDocumentationGuidanceTest.java` | Unit | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ Added failing assertions for endpoint wording + mandatory future-refactor wording | ✅ `./gradlew test --tests "dio.budgeting.ArchitectureDocumentationGuidanceTest"` | ✅ README endpoint clarification + README rule + change-spec rule | ➖ None needed |
