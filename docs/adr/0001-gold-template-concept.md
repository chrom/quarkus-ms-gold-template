# ADR 0001: Gold Template Concept (Synchronous Quarkus Microservice Baseline)

**Date:** 2026-04-09  
**Status:** Accepted  

## Context

This repository is a **Gold Template** for a Quarkus-based microservice. Its purpose is to provide a **repeatable baseline** that teams can clone and evolve into a real service without re-inventing foundational architecture and operations every time.

In many organizations the default and most frequent service type is a **synchronous HTTP API** (request/response). Even when a broader platform includes async messaging, most services still need:

- predictable request handling and failure modes
- a clear dependency boundary (e.g., database)
- production-readiness defaults (probes, graceful shutdown, resource boundaries)
- observability end-to-end (metrics, logs, traces) to debug synchronous request flows

This ADR captures what “Gold Template” means here and what architectural constraints it intentionally sets.

## Decision

### 1. Primary interaction model: synchronous HTTP request/response

- **Decision:** The template is optimized for **synchronous** request processing over HTTP (REST-style endpoints), where a request is handled within one call stack and returns a response immediately.
- **Rationale:** This is the most common baseline and the easiest to reason about during onboarding. It is the **foundation image** teams clone first; asynchronous and distributed patterns are layered in **later template versions** (see evolution below).

**What the baseline ships vs what comes next**

This repository is intentionally a **minimal gold baseline** (HTTP + DB + ops). The following are **not bundled in that baseline** so every clone stays small, teachable, and runnable on a laptop without extra brokers or workflow clusters:

- Event-driven orchestration and durable workflows **as the default in this repo today**
- Exactly-once semantics across services **as a promised default**
- Complex async messaging topologies (Kafka/RabbitMQ) **wired in by default**

**Strategic direction:** The template **evolves toward** event-driven integration, orchestration, and durable workflows as **documented next steps** (messaging, outbox, idempotency, sagas — see **ADR 0008**). The baseline remains synchronous so teams master observability, migrations, and deployment first; event-driven capabilities are **additive forks or upstream milestones**, each with its own ADR.

#### Why the baseline starts synchronous (rationale for “not default yet”)

1. **Event-driven orchestration and durable workflows** — Engines and long-running sagas add failure modes, versioning, worker deployment, and observability that differ from a single-process HTTP handler. Shipping them in **v1** of the template would raise onboarding cost and local stack complexity before HTTP+DB+ops are second nature. **Direction:** adopt orchestration and choreography **after** the baseline is stable; track in ADR 0008 (events, idempotency, optional messaging).

2. **Exactly-once semantics across services** — True exactly-once end-to-end across network boundaries is **not a realistic default promise** without specialized infrastructure and strict assumptions. Production systems usually rely on **at-least-once delivery plus idempotency**. Advertising exactly-once in a template would encourage incorrect mental models.

3. **Complex async messaging topologies (Kafka/RabbitMQ) built-in** — Brokers add operational surface (topics, partitions, consumer groups, retries, DLQs, schema evolution). **Direction:** introduce messaging when the roadmap milestone lands, with compose/Kubernetes examples and a dedicated ADR—not in the first-day clone.

### 2. Persistence: one relational database dependency (PostgreSQL)

- **Decision:** The baseline assumes a relational database (PostgreSQL) as the primary state store.
- **Rationale:** JDBC + JPA/Hibernate remains a common enterprise baseline, and it forces the template to model real production concerns (startup order, migrations, readiness, connection pooling).

### 3. Configuration model: environment-specific behavior via Quarkus profiles

- **Decision:** Environment differences (dev/test/prod) are expressed through Quarkus **profiles** and configuration properties.
- **Rationale:** Keeps one codebase with explicit, reviewable configuration deltas instead of “tribal knowledge”.

### 4. Operational baseline: Kubernetes-friendly runtime behavior

- **Decision:** The service provides the standard operational hooks expected in container orchestration:
  - graceful shutdown behavior
  - liveness/readiness endpoints
  - resource-boundary awareness (JVM/container)
- **Rationale:** Most outages and deployment issues for synchronous services come from rollout/probe/resource misconfiguration, not business code.

### 5. Observability: full loop for synchronous debugging

- **Decision:** The template includes a local observability stack and wiring so a developer can debug a synchronous request across:
  - **metrics** (Prometheus)
  - **logs** (Loki via Alloy)
  - **traces** (Jaeger via OTLP)
  - correlation between them in Grafana
- **Rationale:** For synchronous services, fast debugging usually means “find request → logs → trace → confirm impact in metrics”.

### 6. Security stance (template scope)

- **Decision:** The template prioritizes **local developer ergonomics**; production hardening (SSO, HTTPS termination, secret management platform) is intentionally out of scope for the baseline and should be applied by the hosting platform or per-service decisions.
- **Rationale:** A template should be immediately runnable locally; platform-level security differs between organizations.

### 7. Architectural style: pragmatic baseline + optional hexagonal catalog slice

- **Decision:** The repository **defaults to a pragmatic layered Quarkus style** for the codebase as a whole (fast onboarding, idiomatic extensions). Separately, the **`org.acme.catalog` bounded context** implements a **ports & adapters** layout (domain, `application.port.out`, `application.service`, `adapter.in.rest`, `adapter.out.*`) as a **reference implementation**, including ArchUnit rules for the domain package and outbound ports for persistence and recommendations.

- **Rationale:** Teams wanted both: (1) a template that does not force every service to pay the cost of full hexagonal purity, and (2) a **concrete, working example** of how REST/JSON/pagination and DB access stay at adapters while the domain stays framework-free. Details, trade-offs, and observability notes are recorded in **ADR 0007**.

- **What remains “pragmatic” outside catalog:** Not every package is migrated; for example legacy-style beans may still live under `org.acme.service` and are wrapped by catalog adapters where needed.

- **Hexagonal checklist (realized in catalog):** Ports for repositories and external capabilities; JPA/Panache only in outbound adapters; HTTP DTOs and `PagedResponse` only in inbound REST; domain exceptions mapped to HTTP in exception mappers; **ArchUnit 1.4.1+** required for **Java 25** bytecode when enforcing domain imports.

### 8. HTTP API contract: OpenAPI present; breaking-change pipeline not in template

- **What exists:** `quarkus-smallrye-openapi` is on the classpath; resources use MicroProfile OpenAPI annotations; **Swagger UI** is available in dev at `/q/swagger-ui` (see Makefile). The runtime exposes a generated **OpenAPI document** describing the REST contract.

- **What is not done in this repository:** There is **no** checked-in **golden** `openapi.yaml` / **no CI step** that:
  - exports the spec from a build,
  - **diffs** it against a previous revision or `main`,
  - fails the build on **breaking** changes (e.g. removed required fields, incompatible types).

Teams can add that later (e.g. `quarkus.smallrye-openapi.store-schema-directory`, or `curl` the dev endpoint in CI, plus tools such as **openapi-diff**, **oasdiff**, or review in **Spectral**). It is **not automated** in this repository.

## Consequences

- **Positive:** A consistent starting point for synchronous services; faster onboarding; fewer “forgotten basics” (probes, migrations discipline, observability wiring).
- **Positive:** Architectural decisions are explicit and reviewable; deviations (async messaging, different datastore) become conscious choices documented via ADRs.
- **Evolution (next template versions):** Capabilities such as **authn/z hardening, API contract gates in CI, versioning policy, idempotency/events, multitenancy, compliance packaging, rate limiting**, and **automated SLO alerting** are **planned backlog items**, not out of scope forever. See **ADR 0008** for terminology, gap list, and phased plan.
- **Negative:** The baseline may not fit services that are primarily async, streaming, or require specialized persistence without extension; such services should add dedicated ADRs when adopting those models.
- **Negative:** Full hexagonal purity across **all** packages is **not** default; the catalog slice demonstrates the pattern (see ADR 0007). OpenAPI breaking-change automation remains optional in section 8 until implemented per ADR 0008 Phase A.

## Related documents

- ADR 0002 — SRE / production readiness  
- ADR 0003 — Observability stack (Prometheus, Grafana, Loki, Alloy, Jaeger)  
- ADR 0004 — Health checks (liveness, readiness, Kubernetes)  
- ADR 0005 — Database migrations (Flyway, Hibernate validate)  
- ADR 0006 — Logging and centralized logs (JSON, Loki, Alloy)  
- ADR 0007 — Catalog bounded context: ports & adapters, REST/JSON boundaries, ArchUnit  
- ADR 0008 — Platform evolution roadmap (security, contracts CI, versioning, idempotency/events, tenancy, compliance, rate limits, alerting backlog)  
- `docs/roadmap/event-driven-orchestration.md` — detailed checklist for messaging, outbox, orchestration (Phase G)
