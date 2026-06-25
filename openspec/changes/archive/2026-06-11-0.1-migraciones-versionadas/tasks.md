# Tasks — 0.1-migraciones-versionadas

## Review Workload Forecast

- 400-line budget risk: Low
- Chained PRs recommended: No
- Decision needed before apply: No
- Chain strategy: pending

## Phase 1: Flyway setup

- [x] 1.1 Add Flyway dependencies for core and PostgreSQL support in `build.gradle`.
- [x] 1.2 Configure PostgreSQL datasource and disable implicit Hibernate schema updates by switching `ddl-auto` to `validate`.

## Phase 2: Initial migration baseline

- [x] 2.1 Create `src/main/resources/db/migration/V1__Initial_schema.sql` matching `TransactionEntity`.

## Phase 3: Migration verification coverage

- [x] 3.1 Add a passing startup test proving Flyway migrates an empty PostgreSQL database and records schema history version `1`.
- [x] 3.2 Add a passing startup test proving divergent schema history fails fast with a Flyway validation error.
- [x] 3.3 Add a passing validation test proving Hibernate does not implicitly alter the schema when an unmigrated entity field is introduced.

## Phase 4: Developer guidance

- [x] 4.1 Document the local reset flow with `docker compose down -v` after migration divergence.
