# Design: Layered Architecture Adaptation

## Technical Approach

Adopt pragmatic Layered Architecture as the official MVP language, while keeping the existing package slice and runtime behavior intact. The implementation of this change is documentation-first: clarify responsibilities already visible in `domain`, `application`, `assistant`, `infraestructure`, and `config`, then align README/OpenSpec wording with the `architecture-guidance` spec and PRD rules.

## Architecture Decisions

| Decision | Options | Tradeoff | Decision |
|---|---|---|---|
| Official style | Layered vs Hexagonal/Clean | Stricter styles add seams the MVP does not need | Use pragmatic Layered Architecture with clean boundaries |
| AI placement | Treat Spring AI as core vs integration | Making AI “core” couples business rules to framework APIs | Keep Spring AI orchestration in `assistant`/`infraestructure` edges |
| Migration scope | Rename/move packages vs clarify language | Renames create churn and break current references | Keep current packages, including `infraestructure`, and migrate terminology only |

## Data Flow

Manual save:

    HTTP Controller -> Application Service -> Domain model/repository port -> JPA adapter -> PostgreSQL

AI capture with confirmation:

    HTTP Controller -> Assistant facade -> Spring AI interpret/transcribe -> TransactionDraft
                                                      |
                                                no persistence yet
    User confirms -> HTTP Controller -> Application Service -> Repository -> PostgreSQL

Auth is cross-cutting infrastructure: Spring Security establishes the authenticated user, and application services consume only `AuthenticatedUserProvider`.

Dashboard/history should read from application-level query services that aggregate persisted transactions; they must not read directly from JPA entities in controllers.

## Layer Map

| Layer | Current packages/files | Responsibility |
|---|---|---|
| Entry/Transport | `infraestructure/http/*`, root `assistant/*Controller` | HTTP, request/response mapping, status codes, multipart/JSON handling |
| Application | `application/*`, `application/auth/*`, `application/security/*` | Use-case orchestration, transaction boundaries, user-scoped operations, tool methods exposed to AI |
| Domain | `domain/*`, `domain/user/*` | Core entities, enums, repository contracts, business invariants |
| Infrastructure | `infraestructure/persistence/*`, `infraestructure/security/*`, `assistant/TransactionAssistantFacade.java`, `config/*` | JPA, Spring Security, Spring AI, Flyway, framework wiring |

## Dependency Rules and Anti-Rules

- `domain` MUST NOT depend on Spring, JPA, Security, or Spring AI.
- `application` MAY depend on domain contracts and on small internal abstractions such as `AuthenticatedUserProvider`, but MUST NOT depend on JPA entities or `SecurityContextHolder`.
- Controllers MUST stay thin; validation/transport mapping is allowed, business decisions are not.
- Persistence/security/AI adapters MAY depend inward on application/domain.
- Anti-rules: no casual ports/adapters explosion, no controller-to-entity exposure, no direct persistence from AI interpretation, no rename of `infraestructure` in this change.

## Interfaces / Contracts

```java
// Confirmation-first contract
TransactionDraft interpret(String prompt);   // draft only, no save
TransactionOutput create(PersistTransactionInput input); // explicit persistence path

// User-scoping contract
interface AuthenticatedUserProvider {
    Long requireCurrentUserId();
}
```

`TransactionAssistantFacade` should remain an orchestrator, not a domain service. `TransactionService` remains the persistence entry point until a confirmed draft is submitted.

## File Changes

| File | Action | Description |
|---|---|---|
| `openspec/changes/archive/2026-06-29-layered-architecture-adaptation/design.md` | Archived | Records the technical design and dependency rules |
| `README.md` | Modify | Replace clean/hexagonal wording with layered guidance and non-goals |
| `openspec/config.yaml` | Modify | Keep architecture context and guardrails aligned with layered language |
| `openspec/specs/architecture-guidance/spec.md` | Created | Active architecture guidance synced during archive |

## Testing Strategy

| Layer | What to Test | Approach |
|---|---|---|
| Unit | Service boundaries and confirmation-first rules | JUnit 5 service tests around `TransactionService` and assistant orchestration |
| Integration | Security, Flyway, HTTP contracts | `BudgetingApplicationTests`, `FlywayMigrationIT`, controller/security integration tests |
| E2E | Live AI path only when credentials exist | `ToolCallingIT` as env-gated proof, not default safety check |

## Migration / Rollout

No migration required. Roll out by updating OpenSpec/README terminology first, then apply only minimal code/doc cleanup that removes misleading architectural claims. Existing endpoints, auth flow, DB schema, and package names remain stable.

## Decisions Carried Forward

- The root `assistant` package stays separate for now and is documented as infrastructure-owned AI integration.
- Dashboard/history queries should start in `application` + `infraestructure/persistence`; introduce a dedicated read-model package only if real MVP complexity appears.
