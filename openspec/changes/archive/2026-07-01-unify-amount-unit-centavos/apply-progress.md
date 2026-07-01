# Apply Progress: unify-amount-unit-centavos

## Summary

This apply step resolved two CRITICAL blockers identified by sdd-verify:

1. **Blocker #1**: Cross-endpoint wire inconsistency — `TransactionService.toOutput` was dividing `entry.amountCents()` by 100, converting history line items from centavos to pesos, while other endpoints returned centavos.
2. **Blocker #2**: Missing apply-progress.md with TDD Cycle Evidence table (required by strict TDD contract).

Both blockers are now resolved. The per-item wire `amount` is now consistently integer centavos across all endpoints, and the TDD audit trail is documented below.

---

## TDD Cycle Evidence

### Cycle 1: Fix toOutput Cross-Endpoint Wire Inconsistency

**Behavioral change**: Ensure `GET /transactions` (history) returns per-item `amount` in integer centavos, matching `POST /transactions` and `GET /transactions/{category}`.

#### RED — Confirm the test fails for the right reason

- **Command**: `./gradlew test --tests "dio.budgeting.application.TransactionServiceTest.shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange"`
- **Outcome**: ❌ FAILED (as expected)
- **Failure message**: `expected: 5.0 but was: 500.0`
- **Root cause**: The test asserted `output.value() == 5.0` (which is `500 / 100.0`), but the production code in `TransactionService.toOutput` had already been partially corrected to `(double) entry.amountCents()`, which produces `500.0`. The test was still expecting the buggy divided value.
- **Evidence**: Test XML shows `expected: 5.0 but was: 500.0` at `TransactionServiceTest.java:154`.

#### GREEN — Fix the test to expect the correct integer centavos value

- **Command**: `./gradlew test --tests "dio.budgeting.application.TransactionServiceTest"`
- **Change**: Updated `shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` (line 154) from:
  ```java
  assertThat(output.value()).isEqualTo(5.0);
  ```
  to:
  ```java
  assertThat(output.value()).isEqualTo(500.0);
  ```
  Also added per-item assertions to `shouldComputeHistoryTotalsFromAllRepositoryEntries` to lock the centavo values:
  ```java
  assertThat(response.items().get(0).value()).isEqualTo(500.0);
  assertThat(response.items().get(1).value()).isEqualTo(2250.0);
  assertThat(response.items().get(2).value()).isEqualTo(3000.0);
  ```
- **Outcome**: ✅ PASSED (all 7 TransactionServiceTest tests)
- **Evidence**: `BUILD SUCCESSFUL in 2s`, 7 tests passed.

#### TRIANGULATE — Add controller-level test to lock the unit through the HTTP layer

- **Command**: `./gradlew test --tests "dio.budgeting.infraestructure.http.TransactionControllerTest.shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos"`
- **Change**: Added new test `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` that uses a **real `TransactionService`** (not mocked) with a fake repository, exercising the actual `toOutput` method through the HTTP layer:
  ```java
  @Test
  void shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos() throws Exception {
      var fakeRepository = new FakeTransactionRepositoryForControllerTest();
      fakeRepository.historyToReturn = List.of(
          new TransactionHistoryEntry(new TransactionId(1L), "Coffee", 500L, Category.COMIDA, Instant.parse("2026-03-10T08:00:00Z")),
          new TransactionHistoryEntry(new TransactionId(2L), "Subway", 2250L, Category.TRANSPORTE, Instant.parse("2026-03-12T08:00:00Z"))
      );
      var realService = new TransactionService(fakeRepository, () -> 42L);
      var controller = new TransactionController(realService, transactionAssistant);
      var testMvc = MockMvcBuilders.standaloneSetup(controller)
              .setControllerAdvice(new AssistantExceptionHandler())
              .build();

      testMvc.perform(get("/transactions")
                      .param("from", "2026-03-01")
                      .param("to", "2026-03-31"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.items[0].amount").value(500.0))
              .andExpect(jsonPath("$.items[1].amount").value(2250.0))
              .andExpect(jsonPath("$.totalAmountCents").value(2750))
              .andExpect(jsonPath("$.totalAmount").value(27.5))
              .andExpect(jsonPath("$.transactionCount").value(2));
  }
  ```
  This test pins the per-item `amount` at the HTTP layer, ensuring that 500 centavos persisted in the repository appears as `500.0` (not `5.0`) in the JSON response.
- **Outcome**: ✅ PASSED
- **Evidence**: `BUILD SUCCESSFUL in 4s`, triangulation test passes.

#### REFACTOR — Run full test suite to ensure no regressions

- **Command**: `./gradlew test`
- **Outcome**: ✅ PASSED (76 tests, 0 failures, 5 skipped)
- **Evidence**: `BUILD SUCCESSFUL in 45s`. All tests pass, including the updated service tests and the new triangulation test.

---

### Cycle 2: Mechanical Renames (Compile Checkpoint)

**Behavioral change**: None. These were mechanical renames (`amount` → `amountCents`) that did not alter behavior. The compile checkpoint served as the safety net.

| Step | Action | Verification |
|------|--------|--------------|
| Domain rename | `Transaction.amount` → `Transaction.amountCents` | `./gradlew compileJava` failed at all callers (expected RED) |
| Propagate rename | Updated all Java callers, JPQL strings, test fixtures | `./gradlew compileJava compileTestJava` passed (GREEN) |
| Entity field + @Column | `TransactionEntity.amountCents` with `@Column(name = "amount")` | Compile passed |
| Domain records | `TransactionHistoryEntry.amountCents`, `DashboardAggregate.totalAmountCents` | Compile passed |

**Note**: These renames were already completed in a prior apply step. This apply step only fixed the `toOutput` regression and added the triangulation test.

---

### Cycle 3: findHistory totalAmount Bug Fix (Already Complete)

**Behavioral change**: `findHistory` now correctly computes `totalAmount = totalAmountCents / 100.0` (pesos), not `totalAmount = (double) totalAmountCents` (centavos).

This fix was already implemented in a prior apply step. The tests `shouldComputeHistoryTotalsFromAllRepositoryEntries` and `shouldComputeTotalAmountInPesosAsTotalAmountCentsDividedBy100` already assert the corrected behavior:
```java
assertThat(response.totalAmount()).isEqualTo(57.5);
assertThat(response.totalAmount()).isEqualTo(response.totalAmountCents() / 100.0);
```

**Evidence**: Tests pass in the full suite run.

---

### Cycle 4: AI Prompt Update (Already Complete)

**Behavioral change**: `/transactions/interpret` now returns `amount` in integer centavos per the updated system prompt.

This was already implemented in a prior apply step. The test `shouldReturnAmountInCentavosFromInterpretEndpoint` asserts:
```java
.andExpect(jsonPath("$.amount").value(2300))
```

**Evidence**: Test passes in the full suite run.

---

## Files Changed in This Apply Step

| File | Action | Description |
|------|--------|-------------|
| `src/test/java/dio/budgeting/application/TransactionServiceTest.java` | Modified | Updated `shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` to expect `500.0` (centavos) instead of `5.0` (pesos). Added per-item assertions to `shouldComputeHistoryTotalsFromAllRepositoryEntries`. |
| `src/test/java/dio/budgeting/infraestructure/http/TransactionControllerTest.java` | Modified | Added imports for `DashboardAggregate`, `Transaction`, `TransactionHistoryCriteria`, `TransactionHistoryEntry`, `TransactionId`, `TransactionRepository`. Added triangulation test `shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` with a fake repository inner class. |
| `openspec/changes/unify-amount-unit-centavos/tasks.md` | Modified | Added task 5.5 "Author apply-progress.md with TDD Cycle Evidence" and marked it complete. |
| `openspec/changes/unify-amount-unit-centavos/apply-progress.md` | Created | This file. Documents the TDD cycles and validation results. |

---

## Validation Results

| Command | Outcome | Notes |
|---------|---------|-------|
| `./gradlew test` | ✅ PASSED | 76 tests, 0 failures, 5 skipped (environment-gated OpenAI integration tests). `BUILD SUCCESSFUL in 45s`. |
| `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"` | ✅ PASSED | Smoke test passed. `BUILD SUCCESSFUL in 19s`. |
| `./gradlew test --tests "dio.budgeting.FlywayMigrationIT"` | ✅ PASSED (after retry) | First run failed with `NoSuchFileException: build/test-results/test/binary/in-progress-results-generic*.bin`. This is a known Gradle runner cleanup race condition, not a test failure. Retry succeeded: `BUILD SUCCESSFUL in 21s`. |

### Known Issue: Gradle Runner NoSuchFileException

During the first run of `FlywayMigrationIT`, Gradle reported:
```
java.nio.file.NoSuchFileException: build/test-results/test/binary/in-progress-results-generic13600311256219208287.bin
BUILD FAILED in 24s
```

This is a **transient Gradle runner race condition** where the test runner attempts to clean up a temporary binary results file that has already been deleted. It is **not a test failure**. The test XML was not written due to the race, but a retry of the same command succeeded with `BUILD SUCCESSFUL`. This issue has been observed in prior apply steps and does not indicate a problem with the code or tests.

---

## Blockers Resolved

### Blocker #1: Cross-Endpoint Wire Inconsistency ✅ RESOLVED

**Original issue**: `TransactionService.toOutput` was computing `entry.amountCents() / 100.0`, converting history line items from centavos to pesos. This created a cross-endpoint inconsistency:
- `POST /transactions` returned `amount` in centavos (e.g., `1250.0` for 1250 centavos)
- `GET /transactions/{category}` returned `amount` in centavos
- `GET /transactions` (history) returned `amount` in pesos (e.g., `12.5` for 1250 centavos) ❌

**Root cause**: The previous apply step had partially corrected `toOutput` to `(double) entry.amountCents()`, but the test `shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange` still asserted the buggy divided value (`5.0` instead of `500.0`).

**Fix**: Updated the test to expect the correct integer centavos value (`500.0`). Added a triangulation test that exercises the real `TransactionService.toOutput` through the HTTP layer, locking the unit at centavos.

**Evidence**: 
- `TransactionService.toOutput` (line 100) now produces `(double) entry.amountCents()` — no division by 100.
- `TransactionServiceTest` asserts per-item values are integer centavos (500.0, 2250.0, 3000.0).
- `TransactionControllerTest.shouldReturnHistoryItemsWithAmountAsIntegerCentavosNotPesos` pins the unit at the HTTP layer.

### Blocker #2: Missing apply-progress.md ✅ RESOLVED

**Original issue**: `openspec/changes/unify-amount-unit-centavos/apply-progress.md` did not exist. The strict TDD contract requires this artifact with a `TDD Cycle Evidence` table for audit.

**Fix**: Created this file with:
- TDD Cycle Evidence table documenting RED → GREEN → TRIANGULATE → REFACTOR for the `toOutput` fix.
- Documentation of mechanical renames (compile checkpoint).
- Documentation of prior TDD cycles (findHistory totalAmount fix, AI prompt update).
- Validation results for all required commands.
- Explanation of the Gradle runner NoSuchFileException race condition.

**Evidence**: This file exists at `openspec/changes/unify-amount-unit-centavos/apply-progress.md`.

---

## Cross-Endpoint Wire Consistency Matrix

| Endpoint | Wire Field | Unit | Example (1250 centavos) | Status |
|----------|------------|------|-------------------------|--------|
| `POST /transactions` | `amount` | Integer centavos | `1250.0` | ✅ Consistent |
| `GET /transactions/{category}` | `amount` | Integer centavos | `1250.0` | ✅ Consistent |
| `GET /transactions` (history) | `amount` | Integer centavos | `1250.0` | ✅ Consistent (fixed) |
| `POST /transactions/interpret` | `amount` | Integer centavos | `1250.0` | ✅ Consistent |

**Aggregate fields** (not per-item):
- `GET /transactions` (history) returns `totalAmountCents` (centavos) and `totalAmount` (pesos, correctly computed as `totalAmountCents / 100.0`).

---

## Next Steps

1. **Re-run sdd-verify** to confirm both blockers are resolved and the change is ready for archive.
2. **Coordinate with frontend**: The backend now consistently returns `amount` in integer centavos across all endpoints. The frontend `unify-amount-unit-centavos` change must be ready to consume centavos before shipping.
3. **Monitor AI model behavior**: The prompt now instructs the model to return `amount` in centavos. If production traffic shows the model still returning pesos, implement the defensive parse follow-up (multiply by 100 when value looks like pesos).

---

## Risks and Follow-ups (Carried from Proposal/Design)

| Risk | Status | Mitigation |
|------|--------|------------|
| AI model returns pesos from `/transactions/interpret` despite prompt change | OPEN | Ship the prompt fix now; add defensive parse follow-up if production traffic shows drift. |
| Frontend out of sync if backend ships first | OPEN | Ship with coordinated frontend change; withhold `/interpret` centavos toggle until frontend ready. |
| Future `Money` value object (multi-currency) | DEFERRED | Current rename makes future refactor smaller. Out of scope. |

---

## Sign-off

**Status**: ✅ READY FOR RE-VERIFICATION

Both CRITICAL blockers identified by sdd-verify are resolved:
1. ✅ Per-item history `amount` is now integer centavos (no `/100.0` division in `toOutput`).
2. ✅ `apply-progress.md` with TDD Cycle Evidence table is present.

All tests pass. No new Flyway migration. No DB column rename. No commits made by sdd-apply (parent orchestrates commits).

**Recommendation**: Re-run `sdd-verify` to confirm the change is ready for archive.
