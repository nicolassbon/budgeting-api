# Proposal: Transaction Consolidation

## Intent

Consolidate the transaction backend slice so manual transaction flows are easier to understand, test, and evolve without changing API behavior. The current stack has thin use cases and duplicated DTO hops between HTTP, application, and persistence boundaries.

## Scope

### In Scope
- Improve transaction locality around the existing transaction HTTP/application/persistence path.
- Simplify request/input and output/response mapping where it is pure pass-through duplication.
- Keep `POST /transactions` and transaction listing behavior stable.
- Add or adjust focused tests before implementation per strict TDD.
- Keep the implementation plan small enough to plausibly fit a 400-line review budget.

### Out of Scope
- Assistant/demo AI controller consolidation, including root AI controllers and `POST /transactions/ai` orchestration.
- Renaming `infraestructure` to `infrastructure`; preserve the typo in this slice.
- Persistence schema changes, Flyway migrations, API field renames, or response shape changes.
- Broader test-suite restructuring beyond transaction-slice coverage.

## Capabilities

### New Capabilities
- None — this is an internal consolidation with stable external behavior.

### Modified Capabilities
- None — no existing OpenSpec capability specs are present, and requirements should remain unchanged.

## Approach

Use focused tests to lock current transaction API behavior, then reduce pass-through seams in the transaction slice only. Prefer moving mapping closer to the transaction boundary over broad package reshuffles. Keep `FlywayConfig.java`, JPA validation behavior, and the `infraestructure` package name untouched.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` | Modified | Preserve API while simplifying transaction DTO/application calls. |
| `src/main/java/dio/budgeting/application/*Transaction*` | Modified | Reduce thin pass-through use-case/DTO duplication where safe. |
| `src/main/java/dio/budgeting/infraestructure/persistence/*Transaction*` | Modified | Keep persistence adapter compatible with simplified application seam. |
| `src/test/java/dio/budgeting/**` | Modified/New | Add focused transaction coverage before refactor. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Accidental JSON request/response change | Med | Characterize current endpoint behavior with tests first. |
| Review diff exceeds budget | Med | Avoid package rename, AI work, and broad formatting churn. |
| Persistence wiring regression | Low | Do not change schema/Flyway; run Gradle tests. |

## Rollback Plan

Revert the transaction-slice commits/files only. Because no schema or API contract changes are intended, rollback is a code-only revert with no database migration rollback.

## Dependencies

- Strict TDD from `openspec/config.yaml`; test command is `./gradlew test`.
- Java 25 and Gradle wrapper.

## Success Criteria

- [ ] Existing transaction API behavior remains stable.
- [ ] Transaction mapping/application flow has fewer pass-through DTO hops.
- [ ] No assistant/demo AI consolidation or package rename is included.
- [ ] `./gradlew test` passes, or any external-env-gated failures are documented.
