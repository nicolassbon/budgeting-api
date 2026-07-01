## Overview

- Backend-only Spring Boot 4.0.6 + Spring AI + PostgreSQL project.
- Java toolchain is **25** (`build.gradle`). Use the Gradle wrapper.
- There is **no CI, lint, formatter, typecheck, or coverage task** configured. Do not invent them.

## Run and verify

- Full stack in Docker: `docker compose up -d --build`
- DB only for local JVM/IDE runs: `docker compose up -d database`
- Run app locally: `./gradlew bootRun`
- Run all tests: `./gradlew test`
- Fastest deterministic checks:
  - `./gradlew test --tests "dio.budgeting.BudgetingApplicationTests"`
  - `./gradlew test --tests "dio.budgeting.infraestructure.persistence.FlywayMigrationIT"`
- Focus a single live AI test: `./gradlew test --tests "dio.budgeting.infraestructure.ai.ToolCallingIT"`

## Test and env gotchas

- `OPENAI_API_KEY` is required for real AI flows. The OpenAI integration tests are gated by env and will hit the real API when the key is present.
- `infraestructure.persistence.FlywayMigrationIT` is the safest integration test: deterministic, uses Testcontainers Postgres, no OpenAI credentials.
- `spring.docker.compose.skip.in-tests=false` is enabled in `src/main/resources/application.properties`; some Spring tests expect Docker/Compose to be available.
- Copy `.env.example` to `.env` for local setup. Never commit `.env`.

## Architecture map

- `src/main/java/dio/budgeting/BudgetingApplication.java` is the Spring entrypoint.
- Transaction logic lives under the layered slice `dio.budgeting.{domain,application,infraestructure,config}`.
- AI orchestration lives in `dio.budgeting.infraestructure.ai`.
- Assistant/demo HTTP controllers live in `dio.budgeting.infraestructure.http.assistant`.
- `infraestructure` is intentionally misspelled in package names. Do **not** rename it casually; that is a dedicated refactor.

## Java style

- Prefer explicit imports at the top of the file. Do **not** use inline fully-qualified class names in method bodies, lambdas, assertions, or builders when a normal import can express the dependency clearly.
- Static helpers used repeatedly in tests, such as AssertJ assertions, should be imported statically at the top instead of referenced with fully-qualified names inline.

## Persistence and migrations

- JPA runs with `spring.jpa.hibernate.ddl-auto=validate`. If you add or change persistence fields, add the Flyway migration in the same change or startup will fail.
- `src/main/java/dio/budgeting/config/FlywayConfig.java` is load-bearing. It manually wires Flyway before Hibernate on Spring Boot 4.0.6. Do **not** delete or "simplify" it without proving startup still works.
- If local Flyway history diverges, reset with `docker compose down -v && docker compose up -d`. Do not patch `flyway_schema_history` manually.

## API quirks worth preserving

- `POST /transactions/ai` is the voice -> transcription -> LLM tool-calling -> TTS flow.
- TTS endpoint is spelled `POST /api/sinthesize` in code. Preserve compatibility unless the change explicitly includes an API rename.
- The assistant prompt lives in `src/main/resources/prompts/system-message.st`; the controller loads that exact file.

## Repo workflow

- `openspec/config.yaml` exists and enables **strict_tdd: true** with `./gradlew test` as the test command.
- If you need a compact verification step before a larger run, prefer `BudgetingApplicationTests` + `infraestructure.persistence.FlywayMigrationIT`, then run the full suite if your change could affect AI or wiring.
