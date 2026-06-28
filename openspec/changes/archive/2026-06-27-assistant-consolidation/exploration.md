## Exploration: assistant/AI flow consolidation

### Current State
The assistant surface is split across four root-package demo controllers plus one transaction controller that still owns the real AI orchestration.

- `ChatClientController`, `ChatModelController`, `TranscriptionController`, and `TextToSpeechController` are thin `/api/*` adapters in `dio.budgeting`.
- `TransactionController` still wires the transaction AI flow directly: it loads `classpath:/prompts/system-message.st`, builds a `ChatClient`, attaches `TransactionService` as tools, then chains transcription → chat → TTS inside `/transactions/ai`.
- `BudgetingApplication` only contributes a generic `ChatClient` bean.
- Tests already reflect this split: the root AI controllers are untested demo endpoints, while `TransactionControllerTest` focuses on HTTP shape and `ToolCallingIT`/OpenAI integration tests cover the AI path.

### Affected Areas
- `src/main/java/dio/budgeting/ChatClientController.java` — demo chat-client endpoint
- `src/main/java/dio/budgeting/ChatModelController.java` — demo chat-model endpoint
- `src/main/java/dio/budgeting/TranscriptionController.java` — demo transcription endpoint
- `src/main/java/dio/budgeting/TextToSpeechController.java` — demo TTS endpoint and `/api/sinthesize` compatibility
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` — current AI orchestration owner
- `src/main/java/dio/budgeting/BudgetingApplication.java` — generic `ChatClient` bean
- `src/main/resources/prompts/system-message.st` — prompt contract used by the transaction flow
- `src/test/java/dio/budgeting/*` — live AI tests and controller contract tests

### Approaches
1. **Assistant facade only** — move the four demo controllers plus the transaction AI orchestration into a new `assistant` slice/facade, while keeping the transaction CRUD controller untouched.
   - Pros: best locality for AI concerns, preserves transaction slice boundary, no package rename required
   - Cons: still touches multiple controller entry points and shared Spring AI bean wiring
   - Effort: Medium

2. **Controller-only grouping** — move just the four root `/api/*` demo controllers under an `assistant` package and leave `/transactions/ai` in place for now.
   - Pros: smallest immediate diff, easy to keep under review budget
   - Cons: leaves orchestration split, so locality improves only partially
   - Effort: Low

3. **Full assistant module** — create an assistant service/facade, move `/transactions/ai` orchestration into it, and have controllers delegate to the new module.
   - Pros: cleanest end state, isolates prompt loading and multimodal chain behavior
   - Cons: likely exceeds the 400-line review budget if combined with endpoint moves and test updates
   - Effort: High

### Recommendation
Do **not** consolidate the whole assistant flow in one pass. The safest first slice is **controller-only grouping of the four root AI demo endpoints**, with the transaction AI orchestration left where it is for the next slice. That keeps behavior stable, avoids dragging transaction CRUD into the AI refactor, and gives a low-risk locality win.

### Risks
- `TransactionController` currently constructs the `ChatClient` inline; moving it too soon could change tool registration or prompt loading order.
- `system-message.st` is load-bearing and path-sensitive; relocating it without a compatibility bridge would break startup or AI behavior.
- `/api/sinthesize` is a compatibility endpoint and must not change accidentally while reorganizing controllers.
- Spring AI tool metadata is already contract-tested; refactoring the orchestration boundary can invalidate those assumptions if the tool host changes.

### Ready for Proposal
Yes — but only as a **chain**.
Start with the root `/api/*` assistant controllers as slice 1, then follow with a dedicated assistant service/facade that absorbs `/transactions/ai` once the boundary is proven.

### Conclusion
- **Recommended scope:** only the four root AI demo controllers in the first slice; keep transaction AI orchestration separate.
- **One slice or chained?** Chained. The full assistant consolidation should not be one SDD change.
- **Likely review-size risk:** the full assistant consolidation is medium-to-high risk for exceeding 400 lines; the controller-only slice should fit comfortably.
- **Safest next planning boundary:** `dio.budgeting` AI demo controllers → `dio.budgeting.assistant` package, with zero behavior changes and no transaction controller edits yet.

skill_resolution: java-coding-standards, java-springboot
