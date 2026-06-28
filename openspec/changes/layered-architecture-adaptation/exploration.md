## Exploration: layered architecture adaptation

### Current State
The codebase is already closer to a pragmatic Layered Architecture than to strict Hexagonal/Clean Architecture.

- `domain/` holds core models and repository interfaces (`Transaction`, `User`, `TransactionRepository`, `UserRepository`).
- `application/` holds use-case/service orchestration (`TransactionService`, `AuthService`) and small auth/security ports.
- `infraestructure/` contains HTTP, persistence, and Spring Security adapters.
- Spring AI orchestration is still mixed into the assistant layer, but `TransactionAssistantFacade` now acts as a single facade for transcription, interpretation, and TTS.
- Docs are mixed: `README.md` still describes the code as “clean / hexagonal”, while `openspec/config.yaml` and recent specs already describe a layered slice and preserve the `infraestructure` typo.
- `docs/PRD.md` is product-first and requires auth, manual expense handling, AI-assisted capture, confirmation before save, and a dashboard for the MVP.

### Affected Areas
- `docs/PRD.md` — defines the MVP scope that should drive the architecture decision.
- `README.md` — currently uses Clean/Hexagonal wording that conflicts with the intended pragmatic layered direction.
- `openspec/config.yaml` — already records layered-slice expectations and the `infraestructure` spelling constraint.
- `src/main/java/dio/budgeting/application/*` — application layer is the main place to keep use cases and auth orchestration.
- `src/main/java/dio/budgeting/infraestructure/*` — HTTP/persistence/security adapters remain the infrastructure boundary.
- `src/main/java/dio/budgeting/assistant/*` — AI orchestration is the most visible area of mixed responsibilities.

### Approaches
1. **Keep the current layered slice and document it clearly** — preserve domain/application/infrastructure boundaries, reduce Clean/Hexagonal language, and only trim obvious over-abstraction.
   - Pros: smallest change, aligns with MVP speed, low review risk
   - Cons: some historical naming and AI seams remain
   - Effort: Low

2. **Refactor toward strict Clean/Hexagonal** — add ports/adapters everywhere, split assistant orchestration more aggressively, and formalize inward dependencies.
   - Pros: architectural purity, clearer dependency rules
   - Cons: unnecessary for MVP, larger diff, more indirection, higher cognitive load
   - Effort: High

### Recommendation
Adopt **pragmatic Layered Architecture** as the documented decision.

Keep the current domain/application/infrastructure slicing, preserve the `infraestructure` package name, and remove Clean/Hexagonal claims from contributor-facing docs. For the MVP, only adapt the code where it removes confusion or obvious pass-through seams; do not introduce more abstraction than the product needs.

### Risks
- Docs may continue to imply a stricter architecture than the code actually uses.
- Over-correcting toward Hexagonal could add seams the MVP does not need.

### Ready for Proposal
Yes — the next step is to write the architecture decision/spec update that explicitly names the project as a pragmatic layered backend for the MVP.
