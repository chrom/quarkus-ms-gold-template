# ADR 0004: Health Checks (Liveness, Readiness, Kubernetes)

**Date:** 2026-04-08  
**Status:** Accepted  

## Context

Container orchestrators (Kubernetes and similar) need **explicit signals** to decide whether to **restart** a process, **route user traffic** to it, or **wait** during a slow cold start. Using a single endpoint for everything—or putting heavy dependencies on liveness—causes **avoidable restarts**, **traffic to unhealthy instances**, or **false negatives** during startup.

This service uses **Quarkus SmallRye Health**, which implements **MicroProfile Health** and exposes grouped HTTP endpoints under `/q/health/*`.

**Related:** ADR 0002 (SRE baseline, including probe separation at a high level), ADR 0003 (metrics and observability—health complements but does not replace SLIs).

## Decision

### 1. Framework and endpoints

- **Decision:** Use **`quarkus-smallrye-health`** (MicroProfile Health) as the single mechanism for application health reporting.
- **Decision:** Rely on Quarkus defaults for grouped URLs:
  - **Liveness:** `GET /q/health/live`
  - **Readiness:** `GET /q/health/ready`
- **Decision:** Optionally document or add a **startup** group (`/q/health/started`) when cold starts (e.g. native image, slow dependencies) exceed safe liveness `initialDelaySeconds`; not required for the current JVM dev profile baseline.

### 2. Liveness: minimal work

- **Decision:** Implement liveness as a **trivial “process is up”** check (`org.acme.health.LivenessHealthCheck`), with **no database** and **no external I/O**.
- **Rationale:** Liveness failure triggers **pod restart**; expensive or flaky checks here amplify outages.

### 3. Readiness: critical dependencies with bounded time

- **Decision:** Implement readiness with **`org.acme.health.DatabaseReadinessHealthCheck`**, validating the JDBC path to PostgreSQL via **`Connection.isValid(int)`** with a **5 second** timeout.
- **Rationale:** Readiness controls **traffic membership**; the database is required for correct request handling. A bounded validation avoids hanging probes.

### 4. Built-in extension checks and metrics

- **Decision:** Enable **SmallRye Health extension checks** and **per-check metrics** in configuration:

```properties
quarkus.smallrye-health.extensions.enabled=true
quarkus.smallrye-health.check.metrics.enabled=true
```

- **Rationale:** Surfaces standard checks (where applicable) and Micrometer integration for observability alongside Prometheus (see ADR 0003).

### 5. Kubernetes probe mapping

- **Decision:** Align `Deployment` probes with the endpoints above, as in `deploy/k8s/k8s-deployment-example.yaml`:
  - `livenessProbe.httpGet.path` → `/q/health/live`
  - `readinessProbe.httpGet.path` → `/q/health/ready`
- **Decision:** Keep probe **timeouts** and **periods** consistent with application response time (avoid readiness timeout shorter than DB validation).

### 6. Verification

- **Decision:** Cover live and ready endpoints with REST tests (`HealthEndpointTest`) expecting **UP** and readiness naming aligned with the PostgreSQL check.

## Consequences

- **Positive:** Clear separation between **restart** vs **traffic** decisions; Kubernetes can drain pods when the DB is unavailable without unnecessary JVM restarts; aligns with production readiness goals in ADR 0002.
- **Positive:** Health remains **cheap** on the liveness path and **bounded** on readiness.
- **Negative:** Operators must **retune** probes (and possibly add startup probes) for **native builds** or **new mandatory dependencies**—documented operationally in `docs/observability/health-checks.md`.
- **Negative:** Readiness does not encode **every** optional dependency; adding one that blocks serving traffic should **extend** readiness checks and be reflected in probe timeouts.

## References

- Operational detail and roadmap: `docs/observability/health-checks.md`
- Example probes: `deploy/k8s/k8s-deployment-example.yaml`

