# Proposal: Layered Architecture Adaptation

## Intent

Document the MVP architecture as a pragmatic Layered Architecture with clean boundaries, not strict Hexagonal or full Clean Architecture. This aligns the codebase and contributor docs with `docs/PRD.md`: a one-week challenge needs a coherent demo across auth, AI-assisted capture, confirmation-before-save, manual expense management, and dashboard/history without unnecessary abstraction.

## Scope

### In Scope
- Align architecture documentation first: OpenSpec context, README/contributor-facing wording, and architecture decision language.
- Define clear boundaries for `domain`, `application`, `infraestructure`, `assistant`, and `config` packages.
- Allow minimal package/code cleanup only if later phases need it to remove misleading seams or documentation drift.
- Preserve existing endpoint and package compatibility, including the `infraestructure` typo.

### Out of Scope
- Strict Hexagonal Architecture, full Clean Architecture, or broad ports/adapters refactors for the MVP.
- Renaming `infraestructure` without a dedicated approved rename change.
- Product feature implementation, endpoint redesign, persistence migrations, or AI behavior changes.

## Capabilities

### New Capabilities
- `architecture-guidance`: Documents the official MVP architecture, package boundaries, and compatibility constraints.

### Modified Capabilities
- None.

## Approach

Adopt the exploration recommendation: keep the current domain/application/infrastructure slicing, remove Clean/Hexagonal claims, and use Layered Architecture as the project language. Future implementation phases should prefer documentation alignment before touching Java packages.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `README.md` | Modified | Replace strict Clean/Hexagonal wording with pragmatic layered architecture language. |
| `openspec/config.yaml` | Modified | Keep architecture context aligned with the official decision. |
| `openspec/specs/architecture-guidance/spec.md` | New | Capture architecture requirements for future changes. |
| `src/main/java/dio/budgeting/**` | Modified | Minimal cleanup only if required later; no behavior changes in proposal phase. |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Docs keep implying stricter architecture than the code follows | Med | Make layered architecture explicit in specs and docs. |
| Over-engineering delays MVP delivery | Med | Mark strict Hexagonal/Clean as non-goals. |
| Package rename breaks compatibility | Low | Preserve `infraestructure` unless separately approved. |

## Rollback Plan

Revert documentation/spec changes and any later minimal package cleanup in the change PR. Since proposal scope avoids behavior, migration, and endpoint changes, rollback should not require database or API recovery.

## Dependencies

- `docs/PRD.md` MVP scope and one-week challenge constraints.
- Existing OpenSpec capabilities: `assistant-demo-api`, `transaction-api`, `user-auth`.

## Success Criteria

- [ ] Architecture docs name pragmatic Layered Architecture as official.
- [ ] Strict Hexagonal/Clean Architecture is explicitly out of scope for MVP.
- [ ] PRD-driven MVP priorities remain visible in the rationale.
- [ ] `infraestructure` compatibility is preserved.
