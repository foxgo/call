# Repository Guidelines

## Project Structure & Module Organization
This repository is a Java 21 multi-module Maven build rooted at `pom.xml`. Core modules live in `call-common`, `call-ingestion`, `call-search`, and `call-ops`. Java sources follow the standard layout under `src/main/java/com/callcenter/...`; tests live in `src/test/java`; module config stays in `src/main/resources/application.yml`. Infrastructure files are under `deploy/`, including `deploy/docker-compose.yml` and service-specific Dockerfiles. Design notes and implementation plans belong in `docs/` and `docs/plans/`.

## Build, Test, and Development Commands
Use Maven from the repository root unless you are working on a single module.

- `mvn clean test`: compile all modules and run unit/integration tests.
- `mvn clean package`: build Spring Boot jars for all services.
- `mvn -pl call-ingestion -am spring-boot:run`: run one service plus required modules locally.
- `mvn -pl call-search test`: run tests for a single module.
- `docker compose -f deploy/docker-compose.yml build`: build local images.
- `docker compose -f deploy/docker-compose.yml up -d`: start MySQL, Redis, RocketMQ, Elasticsearch, and the services.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, `PascalCase` classes, `camelCase` methods/fields, and lowercase package names under `com.callcenter`. Keep Spring components focused and small; shared DTOs and infrastructure utilities belong in `call-common`. Prefer descriptive service names such as `CallRecordIngestionService`. There is no checked-in formatter or Checkstyle config, so match surrounding code closely before introducing broader style changes.

## Testing Guidelines
Tests use JUnit 5, Spring Boot Test, Mockito, and Testcontainers. Name unit tests `*Test` and broader container-backed tests `*IntegrationTest`. Keep test fixtures near the owning module, and place any test-only resources under `src/test/resources`. Run `mvn test` before opening a PR; use module-scoped test commands when iterating.

## Commit & Pull Request Guidelines
Recent history shows short subjects such as `mq` and imperative fixes like `Fix shard id calculation and logger`. Prefer the clearer form: short, imperative, and scoped, for example `ingestion: add outbox retry test`. Keep each commit focused. PRs should include a summary, affected modules, config or schema impacts, linked issues, and screenshots or sample payloads when API or ops behavior changes.

## Configuration & Ops Notes
Runtime settings are environment-driven through each module’s `application.yml` files. Common local variables include `MYSQL_HOST`, `ROCKETMQ_NAME_SERVER`, `REDIS_HOST`, `ES_URI`, `ES_USERNAME`, and `ES_PASSWORD`. Do not commit secrets or ad hoc environment overrides.
