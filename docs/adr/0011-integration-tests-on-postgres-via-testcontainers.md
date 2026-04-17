# ADR 0011: Integration Tests on PostgreSQL via Testcontainers

**Date:** 2026-04-17
**Status:** Accepted

## Context

Integration tests in the template currently run against whatever in-process database Quarkus Dev Services picks up; in practice that is H2 when no explicit configuration is given, and a Quarkus-managed PostgreSQL container when the OCI runtime is detected. The fallback path is the hazard: on CI runners without Docker, or when a test profile accidentally disables Dev Services, the suite silently reverts to H2 and the coverage claim becomes a lie — migrations that depend on PostgreSQL features (JSONB, `GENERATED ... AS IDENTITY`, partial indexes, specific constraint error codes) never get exercised against the real engine.

Flyway makes this worse rather than better. A migration that is valid SQL on H2 but wrong on PostgreSQL (say, a reserved word used unquoted, or a constraint name collision) fails only on the first stage deploy, which is exactly when the engineering feedback loop is longest. The gold template promises that what passes CI is safe to deploy; that promise is defensible only when the test DB and the prod DB are the same engine family and major version.

Schema-management strategy in `application.properties` is already `%prod.quarkus.hibernate-orm.schema-management.strategy=validate` (ADR 0005). `validate` requires the schema produced by Flyway to be an exact match of the entity model — any silent divergence between H2-tolerated migrations and the strict PostgreSQL schema is a production risk that `validate` cannot catch ahead of time.

## Decision

### 1. All integration tests run against PostgreSQL 16 via Testcontainers

Tests ending in `*IT` are bound to the JVM Quarkus test profile `test` and configured to request a PostgreSQL 16 container through Testcontainers. The chosen image is pinned:

```
postgres:16-alpine   # resolved to a digest at CI time via `docker image inspect`
```

The major version matches the stage/prod deployment. The Alpine variant is chosen for startup time; the digest-pin (added to `pom.xml` `testcontainers.postgresql.image` property) is applied once the deployed version of the real database is confirmed in `infra-bootstrap`.

### 2. `@QuarkusIntegrationTest` is not the default

The default `@QuarkusTest` (JVM mode) with Testcontainers-provided PostgreSQL is sufficient for 95% of cases and is an order of magnitude faster than `@QuarkusIntegrationTest` (which packages and runs the native image on every test run). `@QuarkusIntegrationTest` is reserved for the subset of tests that exist specifically to validate the native packaging (image start-up, reflection hints, reachability) and is **not** expanded to the full suite.

### 3. New test: `FlywayMigrationIT`

A dedicated integration test applies every migration in `src/main/resources/db/migration/` against a clean PostgreSQL container and asserts, against the information schema, that:

1. every expected table, column, constraint, and index exists;
2. primary keys and foreign keys point where the model declares;
3. the migration history table (`flyway_schema_history`) reports `success = true` for every applied version;
4. re-running migrations on the already-migrated DB is a no-op (idempotency against repeated deploys).

The test does **not** re-assert business logic; it asserts that the schema contract matches both Flyway's output and the JPA entities. If a future migration breaks this assumption (typo in a column name, missing index) the test fails on the PR, not after deploy.

### 4. Remove H2 from the dependency graph

H2 is not listed as a test-scoped dependency in `pom.xml`. If a future contributor adds it (for a unit test that genuinely does not need the database), an ArchUnit / enforcer rule rejects the build. The rule is small enough to live next to the other architecture tests rather than requiring a Maven Enforcer plugin configuration.

### 5. Local developer experience

- `make test` continues to run unit tests only (no container).
- `make verify` runs the full suite including IT; documented prerequisite is a running Docker daemon.
- The Makefile prints a clear error if the Docker socket is unreachable, rather than failing deep inside Surefire.
- `make it-pg` is added as a focused shortcut that runs only `*IT` tests.

### 6. CI

The existing `ci.yaml` runs `./mvnw -B verify`. GitHub-hosted runners have Docker; Testcontainers starts PostgreSQL on demand. No external service container block is required — this was considered (Variant A in the Wave 1 discussion) and rejected because:

- Testcontainers handles per-test-class container lifecycle deterministically; the service-container variant shares one DB across the suite and forces test authors to reason about cross-test pollution.
- Testcontainers parity between local and CI is exact, which matters more than the ~3-second-per-class cold-start cost.

## Consequences

- **Positive — schema parity.** The engine that runs in CI is the engine that runs in prod. Migration bugs surface on the PR.
- **Positive — `FlywayMigrationIT` as a contract test.** Future schema changes are regression-tested against the information schema automatically.
- **Positive — H2 cannot silently reappear.** The dependency is excluded and the architecture test blocks re-introduction.
- **Negative — IT suite runs slower than H2-based tests.** Container startup is on the order of 1–3 seconds per test class. Mitigated by `@Testcontainers(disabledWithoutDocker = true)` on unit-test classes that do not need the database, so the cost is paid only by tests that actually use SQL.
- **Negative — Docker is a hard prerequisite for `make verify`.** Documented in the README and checked by the Makefile. Developers without Docker can run `make test` for the unit suite and rely on CI for the integration surface.
- **Negative — reviewer discipline is required to keep IT tests hermetic.** Tests must not depend on the order other tests run. The Testcontainers container is re-created per test class; the application module loads a fresh Quarkus profile; no static JVM state may leak. The ArchUnit layering (ADR 0007) already prevents the worst cross-test couplings.

## Related documents

- ADR 0005 — Database migrations (Flyway)
- ADR 0007 — Catalog bounded context (hexagonal slice + ArchUnit)
- ADR 0008 — Platform evolution roadmap
- `src/main/resources/db/migration/` — Flyway migrations covered by `FlywayMigrationIT`
- `src/test/java/org/acme/catalog/CatalogDomainArchitectureTest.java` — architecture test that will grow to enforce "no H2 on the classpath"
- [Testcontainers — PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/)
- [Quarkus — Testing with a real database (Testcontainers)](https://quarkus.io/guides/getting-started-testing#testing-with-a-real-database)
