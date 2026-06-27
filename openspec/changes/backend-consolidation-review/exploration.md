## Exploration: backend consolidation review

### Current State
The codebase is still split by layers for transactions and by ad-hoc root-package controllers for AI demos.

- Transaction flow is thin and duplicated:
  - HTTP adapter: `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java`
  - Use cases: `src/main/java/dio/budgeting/application/PersistTransactionUseCase.java`, `ListTransactionsByCategoryUseCase.java`
  - DTOs: `PersistTransactionInput`, `TransactionOutput`, `TransactionRequest`, `TransactionResponse`
  - Persistence adapter: `TransactionEntity`, `JpaTransactionRepository`, `TransactionEntityRepository`
- AI flow is split across 4 demo controllers plus the transaction AI endpoint:
  - `ChatClientController`, `ChatModelController`, `TranscriptionController`, `TextToSpeechController`
  - `TransactionController` builds its own `ChatClient` with prompt + tool wiring
- Test coverage is mostly full-context integration:
  - smoke test, Flyway IT, and 4 live OpenAI tests
  - no focused module/slice tests yet
- The package typo `infraestructure` is pervasive and would touch many imports if renamed.

### Affected Areas
- `src/main/java/dio/budgeting/ChatClientController.java` — root AI demo endpoint
- `src/main/java/dio/budgeting/ChatModelController.java` — root AI demo endpoint
- `src/main/java/dio/budgeting/TranscriptionController.java` — root AI demo endpoint
- `src/main/java/dio/budgeting/TextToSpeechController.java` — root AI demo endpoint
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` — AI + transaction orchestration
- `src/main/java/dio/budgeting/application/*` — thin transaction application layer
- `src/main/java/dio/budgeting/infraestructure/persistence/*` — persistence adapter and typoed package tree
- `src/test/java/dio/budgeting/*` — current integration-heavy test strategy

### Approaches
1. **Single giant consolidation change** — move AI, transaction layering, DTOs, package rename, and tests together.
   - Pros: one cleanup pass, final layout is coherent immediately
   - Cons: high review risk, cross-cutting imports everywhere, hard to verify, rename noise dominates signal
   - Effort: High

2. **Two-phase slice plan** — first consolidate transaction locality and tests, then handle AI/module packaging and the package rename separately.
   - Pros: cleaner boundaries, smaller diffs, easier rollback, better test focus
   - Cons: temporary coexistence of old and new structure
   - Effort: Medium

3. **Three-phase plan** — (a) transaction module + DTO simplification, (b) AI module consolidation, (c) rename `infraestructure` to `infrastructure` as a dedicated mechanical pass.
   - Pros: best review locality, rename isolated from behavioral work, lowest chance of accidental breakage
   - Cons: more phases to manage
   - Effort: Medium

### Recommendation
Do **multiple phases**, not one SDD change.

Recommended boundaries:
1. **Phase 1: transaction locality and DTO simplification** — collapse the thin application stack around a single transaction-facing seam and reduce duplicate request/input/response/output hops.
2. **Phase 2: assistant/AI flow consolidation** — gather root controllers and the transaction AI orchestration into one assistant module/facade.
3. **Phase 3: package rename** — rename `infraestructure` to `infrastructure` only after the feature boundaries are stable.

Best first slice: **transaction DTO + application consolidation**. It has the smallest surface area, keeps the API mostly intact, and gives the earliest locality win without dragging in AI wiring or a package rename.

### Risks
- A package rename in the same change would explode review noise and import churn.
- AI tool wiring changes can alter Spring AI tool schemas and prompt behavior.
- DTO consolidation can unintentionally change serialized field names or amount/value semantics.
- The current live OpenAI tests make broad refactors slower to validate.

### Ready for Proposal
Yes — but only as a **chain**, not a single `/sdd-new` change.
The next step should be to open the first phase as a focused change, then chain the AI consolidation and rename behind it.

### Conclusion
- **Single phase or multiple phases?** Multiple phases.
- **Recommended phase boundaries?** Transaction locality first, AI consolidation second, package rename third.
- **Best first implementation slice?** Transaction DTO + application consolidation.
- **Should we go straight into `/sdd-new` for one change or plan a chain?** Plan a chain.
- **Review-size risk vs 400 lines?** The full consolidation is almost certainly over budget; the first slice should stay under budget if it limits itself to transaction-only files.
- **Package rename in same change?** No. Split it out.

skill_resolution: java-coding-standards, java-springboot
