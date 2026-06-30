# Archive Report: Dashboard Spending Metrics

## Change

- **Change**: `dashboard-spending-metrics`
- **Project**: `budgeting-api`
- **Archived at**: 2026-06-29
- **Archived to**: `openspec/changes/archive/2026-06-29-dashboard-spending-metrics/`
- **Mode**: `hybrid` (Engram + OpenSpec filesystem)
- **Strict TDD**: active
- **Skill resolution**: `injected`

## Final Verification

**PASS WITH WARNINGS** — no CRITICAL issues.

The orchestrator explicitly accepted the design warning as a future consideration, not a blocker:
> In-memory dashboard aggregation is acceptable for MVP but should become DB-side `SUM/COUNT/GROUP BY` projections if data grows.

## Observation IDs (Engram Traceability)

| Artifact | Observation ID | Title |
|----------|---------------|-------|
| Explore | #3084 | Explored dashboard metrics backend gap |
| Proposal | #3088 | sdd/dashboard-spending-metrics/proposal |
| Spec | #3090 | sdd/dashboard-spending-metrics/spec |
| Design | #3089 | sdd/dashboard-spending-metrics/design |
| Tasks | #3098 | sdd/dashboard-spending-metrics/tasks |
| Apply progress | #3099 | SDD apply-progress with TDD Cycle Evidence for dashboard-spending-metrics |
| Verify report | #3106 | sdd/dashboard-spending-metrics/verify-report |
| Archive report | *(this observation)* | sdd/dashboard-spending-metrics/archive-report |

## Specs Synced

All delta requirements from the change were merged into the main transaction-api source of truth.

| Domain | Action | Details |
|--------|--------|---------|
| `transaction-api` | Updated | 2 ADDED, 3 MODIFIED, 0 REMOVED, 0 RENAMED requirements |

### ADDED requirements (inserted into main spec)
1. **Transaction date persistence and API response** — persist date/timestamp, default to now, expose in responses
2. **Owner-scoped transaction history endpoint** — `GET /transactions` with period/category filters and totals context

### MODIFIED requirements (updated in place)
1. **Create transactions** — `POST /transactions` MAY accept optional date; response includes `id`, `description`, `category`, `amount`, `date`
2. **List transactions by category** — response shape extended with `date`; backward compatibility preserved
3. **Enforce authenticated transaction access** — now includes the transaction history endpoint

### UNCHANGED requirements (preserved as-is)
- Preserve transaction AI HTTP and tool-call exposure
- Extract transaction AI orchestration behind a dedicated assistant seam
- Prove consolidation with focused compatibility tests
- Enforce per-user transaction ownership
- Confirm AI-interpreted persistence before save

## Archive Contents

- `proposal.md` ✅
- `specs/transaction-api/spec.md` ✅
- `design.md` ✅
- `tasks.md` ✅ (22/22 tasks complete)
- `apply-progress.md` ✅
- `verify.md` ✅

## Source of Truth Updated

The following specs now reflect the new behavior:

- `openspec/specs/transaction-api/spec.md` — merged delta requirements
- `openspec/specs/dashboard-spending-metrics/spec.md` — already existed as standalone dashboard spec

## Design Warning (Preserved)

The dashboard aggregate implementation aggregates in memory after fetching owner-filtered rows rather than using database-side aggregate query/projection. This is acceptable for MVP scale. If data volume grows, replace `TransactionEntityRepository.aggregateByOwnerAndPeriod` with database-side `SUM/COUNT/GROUP BY` projections.

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Ready for the next change.
