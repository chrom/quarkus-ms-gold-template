# ADR 0002: SRE and Production-Readiness Standards

**Date:** 2026-04-08  
**Status:** Accepted  

## Context

As the Quarkus microservice (`quarkus-ms-gold-template`) transitions from local development to a Production Kubernetes environment, we need to ensure it runs reliably under load and operates seamlessly within the Kubernetes execution model. Out of the box, JVM-based microservices and generic Docker deployments expose several configuration vulnerabilities (e.g., poor resource boundaries leading to `OOMKilled`, request dropping during deployments). Furthermore, without Service Level Objectives (SLOs) tied to our metrics, we lack a business-aligned way to measure reliability.

## Decisions

We have decided to implement the following Site Reliability Engineering (SRE) practices and production-ready configurations:

### 1. Kubernetes Resource Limits and JVM Memory Tuning
- **Decision:** Explicitly define Kubernetes `requests` and `limits` in deployment manifests.
- **Decision:** Configure the JVM / Substrate VM to respect container boundaries by passing memory flags (e.g., `-XX:MaxRAMPercentage=75.0`). This reserves 25% of the container memory for off-heap tasks and the OS, preventing sudden `OOMKilled` terminations by the Kubernetes kubelet.

### 2. Graceful Shutdown
- **Decision:** Enable Graceful Shutdown in Quarkus. Upon receiving a `SIGTERM` signal (e.g., during a Pod rollout), the application will stop accepting new requests but wait a predefined timeout (e.g., 10 seconds) for in-flight requests to complete. This ensures zero-downtime deployments.

### 3. Dedicated Kubernetes Health Checks
- **Decision:** Use `quarkus-smallrye-health` to separate Liveness and Readiness probes.
  - **Liveness:** Checks if the JVM is responsive (HTTP ping).
  - **Readiness:** Verifies downstream connections (e.g., database connectivity via Agroal). Kubernetes will only route traffic to the Pod if the Readiness probe passes.

### 4. SLO targets and alerting (policy vs repository state)

- **Decision (policy):** On-call and engineering align on **business-value SLOs** derived from Prometheus/Grafana:
  - **Availability SLO:** 99.9% successful HTTP responses; **alert** if 5xx rate exceeds 0.1% over a sliding 5-minute window.
  - **Latency SLO:** `p95` API latency under 500ms; **alert** on sustained degradation.

- **Implementation status in this repository:** Dashboards and metrics **support** measuring these SLOs (see Grafana dashboard “Quarkus Cloud Template — Overview”). **Grafana/Prometheus alert rules are not yet provisioned as code** in `grafana/` — that gap is **tracked** in **ADR 0008 (Phase F)**. Until then, alerts are a **manual or platform-level** concern (e.g. Grafana UI rules, Mimir, Alertmanager outside this repo).

### 5. Incident Response Runbook
- **Decision:** Establish a foundational `RUNBOOK.md` documentation artifact containing emergency procedures (Rollout/Rollback commands, diagnostic paths, and communication protocols) for the on-call engineer.

## Consequences

- **Positive:** Increased system stability, zero-downtime deployments, predictability under load, and faster Mean Time to Recovery (MTTR) during incidents.
- **Negative:** Increased configuration complexity (managing Helm/K8s manifests alongside Quarkus properties). Alert rules require tuning over time to avoid alert fatigue once provisioned (ADR 0008 Phase F).

## Related documents

- ADR 0008 — Platform evolution roadmap (alerting implementation backlog)  
- ADR 0003 — Observability stack  
- `docs/RUNBOOK.md`

