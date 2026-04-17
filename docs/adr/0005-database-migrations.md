# ADR 0005: Database Migrations (Flyway + Hibernate Validate)

**Date:** 2026-04-08  
**Status:** Accepted  

## Context

Schema changes must be **versioned**, **repeatable across environments**, and **safe during rolling deployments** where old and new application versions share one database. Letting Hibernate auto-generate DDL in production risks **uncontrolled changes** and **drift**. Ad-hoc SQL in runbooks does not scale.

**Related:** ADR 0003 (observability; migrations are orthogonal to metrics but affect startup order and failure modes).

## Decision

### 1. Flyway as the single source of DDL

- **Decision:** Use **`quarkus-flyway`** with migration scripts on the classpath at **`src/main/resources/db/migration`** (`V{version}__Description.sql`).
- **Decision:** Enable **`quarkus.flyway.migrate-at-start=true`** so Flyway runs **before** the Hibernate persistence unit initializes.
- **Rationale:** One ordered, tracked history (`flyway_schema_history`); no duplicate application of the same version on restart.

### 2. Hibernate does not mutate schema in any profile used here

- **Decision:** Set **`quarkus.hibernate-orm.schema-management.strategy=validate`** for **`%dev`**, **`%test`**, and **`%prod`**.
- **Rationale:** Hibernate **checks** that entities match the database; **Flyway** applies DDL. Mismatches fail fast at startup instead of silently diverging.

### 3. Operational pattern for production changes (expand / contract)

- **Decision:** Treat multi-step schema evolution as a **discipline**, not a Flyway feature: design migrations so each release remains compatible with **either** the previous or next application version where rolling deploys apply.
- **Rationale:** Avoids breaking concurrent old/new pods during rollout (e.g. add nullable columns before removing old ones; split “expand” and “contract” across releases when needed).

### 4. Repository layout

- **Decision:** Keep initial and subsequent DDL in versioned SQL files (e.g. `V1__Initial_schema.sql` as the baseline for current entities).

## Consequences

- **Positive:** Predictable schema lifecycle; CI and prod apply the same scripts; Hibernate catches entity/schema drift early.
- **Positive:** Clear ownership: **SQL migrations** for structure, **Java entities** for ORM mapping.
- **Negative:** Every entity change needs a **paired migration** before deploy; `validate` will block startup if they diverge.
- **Negative:** Long-running or locking migrations require **operational planning** (maintenance windows, batching, expand/contract sequencing)—documented in `docs/observability/database-migrations.md`.

## References

- `docs/observability/database-migrations.md`
- `src/main/resources/application.properties` (Flyway + Hibernate sections)
- `src/main/resources/db/migration/`

