# Event-driven orchestration — gap inventory and delivery plan

**Status:** Living document (aligned with [ADR 0008](../adr/0008-platform-evolution-roadmap.md))  
**Last updated:** 2026-04-10  

This document preserves the **checklist of what is missing** in the Gold Template today to support **event-driven integration**, **choreography**, and **durable orchestration** (long-running, compensating workflows). It is **not** a rejection of that direction — see [ADR 0001 §1](../adr/0001-gold-template-concept.md): the baseline is synchronous HTTP first; this is the **backlog to close** for messaging-era services.

---

## 1. Messaging infrastructure

| Gap | Notes |
|-----|--------|
| Message broker in dev/CI | Add Kafka, RabbitMQ, NATS JetStream, or Pulsar to **Docker Compose** / **Testcontainers** for local parity with prod. |
| Topics / queues | Naming conventions; **retry** and **DLQ** policies; dead-letter handling runbooks. |
| Schema for events | Avro / JSON Schema + **schema registry** (or documented convention for JSON-only). |
| Async contracts | Same rigor as OpenAPI: **versioned event contracts**, compatibility rules, optional Pact/async contract tests. |

---

## 2. Application patterns (code)

| Gap | Notes |
|-----|--------|
| **Transactional outbox** | DB commit + outbox row → background publisher; avoids lost events between commit and broker. |
| **Idempotent consumers** | Dedup keys, idempotent handlers; **at-least-once** + safe replays. |
| **Saga / compensation model** | Either **choreography** (events only) or **orchestration** (central coordinator); compensating actions documented per step. |
| **CQRS-light** (optional) | Separate read models if projections are fed by events. |
| **Command / event / query** split | Clear naming and packages so “domain events” are not confused with integration events. |

---

## 3. Orchestration engine (when “workflow” is required)

Choreography alone may not suffice for **long-running**, **human-in-the-loop**, or **strict ordering** processes.

| Gap | Notes |
|-----|--------|
| Engine choice ADR | Temporal, Camunda, Conductor, managed workflow SaaS — **not** in template today. |
| Workers | Deployment model, versioning, **replay** semantics, timeouts. |
| Observability | Workflow-specific traces, metrics (started/completed/failed), DLQ for stuck instances. |

The baseline repo has **no** workflow engine — this is an **explicit** next milestone, not an oversight.

---

## 4. Observability for asynchronous paths

| Gap | Notes |
|-----|--------|
| Trace propagation | W3C trace context (or B3) in **message headers** across producers/consumers. |
| Metrics | Consumer **lag**, partition lag, **DLQ depth**, replay rate. |
| Logs | **Correlation** beyond HTTP `requestId`: event id, partition, offset, trace id. |
| Dashboards | Grafana panels for broker + consumer health (may reuse Prometheus JMX exporters or broker metrics). |

Current stack is optimized for **synchronous request** flows ([ADR 0003](../adr/0003-observability-monitoring-stack.md)); async requires **additional** instrumentation.

---

## 5. Testing and CI

| Gap | Notes |
|-----|--------|
| Broker in IT | Testcontainers (or embedded broker where applicable). |
| Contract tests | **Pact** message contracts or schema-registry CI checks. |
| Load / chaos | Slow consumers, rebalance, poison messages — scripted scenarios. |

---

## 6. Operations

| Gap | Notes |
|-----|--------|
| Runbooks | Stuck messages, **replay** procedures, poison pill handling. |
| Event versioning | **Backward-compatible** evolution, dual-write windows, deprecation policy. |
| Security | ACLs on topics, mTLS to broker, secret rotation for SASL/SCRAM. |

---

## Suggested delivery phases (template milestones)

These map to future updates of [ADR 0008](../adr/0008-platform-evolution-roadmap.md) **Phase G** (see ADR file).

1. **G1 — Broker + dev compose + Testcontainers** (no business logic change).
2. **G2 — Outbox + one domain event published after a catalog mutation** (reference path).
3. **G3 — One consumer + idempotency store + integration test.**
4. **G4 — Observability**: trace + metric + log fields for async path.
5. **G5 — Optional workflow ADR** if a concrete engine is chosen for the template.

---

## Related

- [ADR 0001 — Gold Template concept](../adr/0001-gold-template-concept.md) — baseline vs evolution  
- [ADR 0008 — Platform evolution roadmap](../adr/0008-platform-evolution-roadmap.md) — master backlog  
- [ADR 0003 — Observability stack](../adr/0003-observability-monitoring-stack.md)
