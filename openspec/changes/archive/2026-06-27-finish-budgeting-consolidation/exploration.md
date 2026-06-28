## Exploration: finish-budgeting-consolidation

### Current State
`docs/progress.md` is partially outdated. The first two consolidation slices already landed: transaction CRUD now runs through `TransactionService`, and the four demo `/api/*` endpoints already live under `dio.budgeting.assistant` with focused controller tests. The remaining consolidation is narrower: `/transactions/ai` and `/transactions/interpret` still keep Spring AI orchestration inside `TransactionController`, transaction DTO/mapping duplication still exists across request/input/output/response types, the `infraestructure` typo still exists by design, and test coverage is still uneven around the transaction AI endpoints and shared AI test setup.

### Affected Areas
- `docs/progress.md` — source agenda, but no longer matches the current codebase on completed assistant and transaction slices
- `src/main/java/dio/budgeting/infraestructure/http/TransactionController.java` — still owns transcription, prompt loading, tool wiring, `/transactions/ai`, and `/transactions/interpret`
- `src/main/java/dio/budgeting/BudgetingApplication.java` — still exposes the generic `ChatClient` bean used by demo assistant endpoints
- `src/main/java/dio/budgeting/assistant/ChatClientController.java` — already consolidated under assistant package and still depends on the generic `ChatClient`
- `src/main/java/dio/budgeting/application/TransactionService.java` — current transaction seam and Spring AI tool host
- `src/main/java/dio/budgeting/application/input/PersistTransactionInput.java` — application DTO still depends on HTTP request type
- `src/main/java/dio/budgeting/application/output/TransactionOutput.java` — outward DTO still differs from HTTP response naming (`value` vs `amount`)
- `src/main/java/dio/budgeting/infraestructure/http/request/TransactionRequest.java` — thin HTTP wrapper over the same create shape
- `src/main/java/dio/budgeting/infraestructure/http/response/TransactionResponse.java` — thin HTTP wrapper over the same read shape
- `src/test/java/dio/budgeting/assistant/AssistantDemoControllerTest.java` — proves demo endpoint compatibility is already covered
- `src/test/java/dio/budgeting/assistant/AssistantDiscoveryTest.java` — proves assistant package discovery is already covered
- `src/test/java/dio/budgeting/application/TransactionServiceTest.java` — focused transaction service behavior test
- `src/test/java/dio/budgeting/application/TransactionToolContractTest.java` — guards Spring AI tool metadata and DTO record contract
- `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` — covers CRUD HTTP shape only, not AI endpoints
- `src/test/java/dio/budgeting/ToolCallingIT.java` and `src/test/java/dio/budgeting/OpenAi*.java` — live-provider tests still scattered with repeated env gating

### Approaches
1. **Finish only the safe remaining consolidation** — extract transaction AI orchestration behind an assistant-focused service/facade, collapse the request/input and output/response pairs where contract-safe, and add focused tests for the transaction AI endpoints without renaming `infraestructure`.
   - Pros: aligns with what is actually left, preserves current package-rename guardrails, fits the remaining work into one reviewable change better than a repo-wide cleanup
   - Cons: leaves the `infraestructure` typo and some package inconsistency in place for now
   - Effort: Medium

2. **Include package rename and broader layout cleanup now** — combine the remaining AI/DTO/test cleanup with `infraestructure` -> `infrastructure` and wider package normalization.
   - Pros: cleaner end state in one pass
   - Cons: conflicts with current OpenSpec/repo rules, expands blast radius across imports/tests/specs, and is likely to exceed the preferred review budget
   - Effort: High

### Recommendation
Use **Approach 1**. The codebase already proved that incremental consolidation works: the assistant demo controllers and the transaction service seam are in place. The next safe boundary is to extract the AI orchestration out of `TransactionController`, remove the remaining transaction DTO hop duplication where the external HTTP and tool contracts stay identical, and consolidate test strategy around those seams. Keep the `infraestructure` rename explicitly out of this change; it deserves its own proposal because the repo rules currently preserve that typo on purpose.

### Risks
- Moving `/transactions/ai` orchestration can accidentally change prompt loading, tool registration, or response audio behavior if the new facade does not preserve the current `ChatClient.Builder` sequence.
- Collapsing `TransactionOutput`/`TransactionResponse` or `TransactionRequest`/`PersistTransactionInput` can break Spring AI tool schemas or HTTP JSON field names if contract tests are not updated first.
- The `infraestructure` rename is tempting, but bundling it here would turn a focused consolidation into a high-churn refactor.
- Test consolidation still needs care because `spring.docker.compose.skip.in-tests=false` and live OpenAI tests have different runtime assumptions.

### Ready for Proposal
Yes — tell the user the next proposal should scope ONLY the remaining safe consolidation: transaction AI orchestration extraction, transaction DTO/mapping deduplication, and test strategy cleanup. Explicitly mark `infraestructure` renaming as out of scope for this change.
