# ADR 0006: Application Logging and Centralized Log Pipeline (Loki, Alloy)

**Date:** 2026-04-08  
**Status:** Accepted  

## Context

Operators need **searchable, correlated logs** alongside metrics and traces. Pushing logs directly from every app instance to a store couples the app to log infrastructure and complicates credentials and retries. The stack already includes **Grafana Loki** for storage and **Grafana Alloy** for collection (see ADR 0003); this ADR records **application-level** logging choices and how they connect to that pipeline.

## Decision

### 1. Log to stdout; no direct Loki client in the application

- **Decision:** The application **writes logs to the console** (and optionally to local rotated files). It does **not** embed a Loki HTTP client for normal operation.
- **Rationale:** Container platforms and agents collect **stdout/stderr** reliably; the app stays simple and environment-agnostic.

### 2. Production: structured JSON on the console

- **Decision:** Use **`quarkus-logging-json`** and enable **JSON console logging in production** (`%prod.quarkus.log.console.json.enabled=true`, pretty-print off for line-oriented agents).
- **Rationale:** Structured fields parse cleanly in Loki/Grafana; aligns with centralized indexing and queries.

### 3. Correlation fields (MDC)

- **Decision:** Use console (and file) format patterns that include **MDC** placeholders such as **`requestId`**, **`traceId`**, **`spanId`** for correlation with HTTP access logs and **OpenTelemetry** traces (Jaeger).
- **Rationale:** Jump from logs to traces and vice versa during incidents.

### 4. File appenders and HTTP access log

- **Decision:** Enable **rotating application file logs** under `logs/quarkus.log` with daily suffix and retention limits.
- **Decision:** Enable **HTTP access logging** to a separate file under `logs/` with pattern fields that echo request/trace identifiers.
- **Rationale:** Local retention for debugging; access log as a distinct stream from application logs.

### 5. Collection path for Docker-based local development

- **Decision:** For the **metrics** compose project (`deploy/docker-compose-metrics.yml`), **Grafana Alloy** collects container logs (e.g. `loki.source.docker`) and **writes** to Loki; Grafana explores LogQL.
- **Decision:** Accept that **host-only** `quarkus:dev` stdout is **not** ingested by the default Alloy-over-Docker-socket path—only containerized processes appear in that pipeline unless the collector is configured otherwise.

**Related:** ADR 0003 (full observability stack topology, Loki config files, Alloy River config).

## Consequences

- **Positive:** Consistent structured logs in prod; correlation with tracing; no Loki-specific code paths in the service.
- **Positive:** Operators can swap collectors (Alloy vs future agents) without changing application code.
- **Negative:** Disk usage and I/O from file appenders—must be sized and rotated (already constrained in `application.properties`).
- **Negative:** Teams must remember **JSON prod** vs **text dev** when reading raw console output; MDC must be populated for correlation to work end-to-end.

## References

- `docs/observability/logging-loki-alloy.md`
- `src/main/resources/application.properties` (sections 5–6: application logs, access log)
- `pom.xml` — `quarkus-logging-json`

