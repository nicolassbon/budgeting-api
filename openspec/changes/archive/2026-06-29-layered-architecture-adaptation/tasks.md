# Tasks: Layered Architecture Adaptation

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 80–180 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | pending |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: pending
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Align docs and config to pragmatic Layered Architecture | Single PR | README + openspec/config.yaml + spec wording; no behavior changes |
| 2 | Minimal code/annotation clarification only if audit finds drift | Single PR | Javadoc/ownership fixes; keep `infraestructure` unchanged |

## Phase 1: Documentation Alignment

- [x] 1.1 Rewrite `README.md` architecture section to name pragmatic Layered Architecture as the official MVP style.
- [x] 1.2 Add explicit non-goals to `README.md`: strict Hexagonal and full Clean Architecture are out of scope for the MVP.
- [x] 1.3 Update `README.md` layer descriptions to match `domain`, `application`, `infraestructure`, `assistant`, and `config` responsibilities.
- [x] 1.4 Align `openspec/config.yaml` `context` field with the layered architecture decision and PRD-driven MVP rationale.
- [x] 1.5 Review the architecture-guidance spec for final wording consistency with `design.md`; after archive, the active spec lives at `openspec/specs/architecture-guidance/spec.md`.

## Phase 2: Minimal Code Clarification

- [x] 2.1 Audit Java source for comments, class names, or annotations that imply Clean/Hexagonal ownership contrary to the layered decision.
- [x] 2.2 Add or update Javadoc on `assistant/TransactionAssistantFacade.java` to state it is an infrastructure-owned AI orchestrator, not a domain service.
- [x] 2.3 Verify `domain` package contains no Spring, JPA, Security, or Spring AI imports; document any exception found.
- [x] 2.4 Confirm `infraestructure` package spelling remains unchanged and compatibility is preserved.

## Phase 3: Verification

- [x] 3.1 Run `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` to confirm no startup/regression.
- [x] 3.2 Run `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` to confirm no schema/migration drift.
- [x] 3.3 Grep the repo for leftover "clean architecture", "hexagonal", or "ports and adapters" claims in docs and source comments.
