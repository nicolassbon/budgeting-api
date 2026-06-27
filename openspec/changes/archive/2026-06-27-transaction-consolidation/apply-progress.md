# Apply Progress: transaction-consolidation

**Change**: `transaction-consolidation`  
**Project**: `budgeting-api`  
**Artifact store**: `openspec`  
**Mode**: Strict TDD  
**Recovered on**: 2026-06-27

## Evidence recovery note

This change already existed in the working tree when this apply pass started, but no `apply-progress` artifact had been written.
This file reconstructs the evidence that is still directly observable and separates it from the new work completed in this pass.

- Directly observable historical evidence: existing test files, current task checklist, file timestamps, deleted use-case files, and passing reruns.
- Not recoverable exactly: original RED command output for every earlier task row was not preserved at the time of implementation.
- New work in this pass: added the missing non-empty `GET /transactions/{category}` HTTP contract test and reran the focused + full test commands.

## TDD Cycle Evidence

| Task | Scope | RED evidence | GREEN evidence | Safety net / notes |
|------|-------|--------------|----------------|--------------------|
| 1.1 | `TransactionControllerTest` contract coverage | Existing test file timestamp `2026-06-27 11:19:26 -0300` predates `TransactionService.java` / controller consolidation timestamps `2026-06-27 11:22:23 -0300`, which supports contract-first work for the controller slice. | `./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.application.TransactionToolContractTest"` passed on 2026-06-27. | This apply pass added the missing non-empty GET case so the HTTP contract now covers create, empty list, and non-empty list. |
| 1.2 | `TransactionToolContractTest` tool schema lock | Exact original RED command output was not preserved. The test file exists and asserts the preserved tool names, descriptions, and record/constructor field names. | Focused transaction test command passed on 2026-06-27. | Historical chronology for this single row is reconstructed from surviving artifacts only; no fake timestamp was added. |
| 1.3 | `TransactionServiceTest` service behavior lock | Existing test file timestamp `2026-06-27 11:19:26 -0300` predates `TransactionService.java` timestamp `2026-06-27 11:22:23 -0300`, which supports test-first coverage for create/list consolidation behavior. | Focused transaction test command passed on 2026-06-27. | Fake repository assertions still prove repository delegation and output mapping. |
| 2.1-2.3 | Consolidate into `TransactionService` and remove thin use cases | Surviving artifacts show the service tests existed before the consolidated service file timestamp, but exact per-task RED outputs were not preserved. | `TransactionServiceTest` and `TransactionToolContractTest` pass; `PersistTransactionUseCase.java` and `ListTransactionsByCategoryUseCase.java` are deleted in the workspace. | Recovery is honest: current workspace proves the GREEN state, while earlier RED console output is unavailable. |
| 3.1-3.3 | HTTP wiring and adapter-local mapping | `TransactionControllerTest` existed before the controller consolidation timestamp; exact per-task RED output was not preserved. | `TransactionControllerTest` passes with `POST /transactions` and both empty/non-empty `GET /transactions/{category}` assertions. | No schema/entity change was introduced for this slice. |
| 4.1 | Focused regression suite | N/A — verification task. | Focused transaction command passed on 2026-06-27. | Command: `./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.application.TransactionToolContractTest"` |
| 4.2-4.3 | Broader regression suite | N/A — verification task. | `./gradlew test --rerun-tasks` passed on 2026-06-27. JUnit XML summary after rerun: `11 passed / 0 failed / 6 skipped`. | Skips remain the existing env-gated OpenAI/application-context tests; no new test weakening was introduced. |

## Work completed in this apply pass

1. Added `TransactionControllerTest.shouldListTransactionsWithStableHttpContract()`.
2. Re-ran the focused transaction tests.
3. Re-ran `./gradlew test --rerun-tasks` to refresh full-suite evidence.
4. Wrote this recovered `apply-progress` artifact so verify can inspect the surviving strict-TDD evidence explicitly instead of inferring it.

## Commands run in this pass

```text
./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest" --tests "dio.budgeting.application.TransactionServiceTest" --tests "dio.budgeting.application.TransactionToolContractTest"
BUILD SUCCESSFUL in 4s

./gradlew test --rerun-tasks
BUILD SUCCESSFUL in 28s
```

## Current JUnit summary after full rerun

```text
suites=10 passed=11 failed=0 skipped=6
dio.budgeting.BudgetingApplicationTests: tests=1 failed=0 skipped=1
dio.budgeting.FlywayMigrationIT: tests=3 failed=0 skipped=0
dio.budgeting.OpenAiChatClientIT: tests=1 failed=0 skipped=1
dio.budgeting.OpenAiChatModelIT: tests=1 failed=0 skipped=1
dio.budgeting.OpenAiSpeechModelIT: tests=1 failed=0 skipped=1
dio.budgeting.OpenAiTranscriptionModelIT: tests=1 failed=0 skipped=1
dio.budgeting.ToolCallingIT: tests=1 failed=0 skipped=1
dio.budgeting.application.TransactionServiceTest: tests=2 failed=0 skipped=0
dio.budgeting.application.TransactionToolContractTest: tests=3 failed=0 skipped=0
dio.budgeting.infraestructure.http.TransactionControllerTest: tests=3 failed=0 skipped=0
```
