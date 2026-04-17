# ADR 0008: Platform Evolution Roadmap (Next Major Capabilities)

**Date:** 2026-04-10  
**Status:** Accepted  

## Context

The Gold Template (ADR 0001) delivers a **working HTTP + PostgreSQL + operations baseline** with observability and a reference catalog bounded context (ADR 0007). That baseline is **intentionally complete for cloning and teaching**; several capabilities expected in **large production systems** are **not yet implemented** in this repository. They are **not rejected** — they are **scheduled evolution work** for subsequent template versions.

This ADR records **what is missing**, **correct terminology**, and a **phased plan** so teams can prioritize forks and upstream contributions.

## Glossary (what we mean by “API security limits”)

When people say “there are no limits on the API for security,” they usually mean one or more of:

| Term (EN) | Typical UA | Meaning |
|-----------|------------|--------|
| **Rate limiting** | обмеження частоти запитів | Cap requests per time window per client/IP/API key (e.g. token bucket). |
| **Throttling** | дроселювання | Slow down or reject excess load under pressure (may overlap with rate limits). |
| **Quota / consumer quota** | квоти споживача | Fixed allowance per client or subscription period. |
| **API abuse protection** | захист від зловживань | Combination of limits, anomaly detection, WAF at edge. |
| **DoS mitigation (application layer)** | пом’якшення DoS на рівні застосунку | Limits + timeouts; full protection often also needs **edge/gateway** (CDN, API Gateway, K8s Ingress policies). |

**Today:** the template does **not** ship application-level rate limiting or an API gateway policy pack; that gap is listed below.

## Gap inventory → target outcomes

| Area | Current template state | Target outcome (next versions) |
|------|------------------------|--------------------------------|
| **Security** | Threat model lite; optional **`secured`** profile (`quarkus-oidc`, `GET /api/secured/me`); unauthenticated routes unchanged by default — [`oidc-secured-profile.md`](../security/oidc-secured-profile.md) | Secrets via platform; TLS at ingress; Phase B.3–4 (rate limiting, security headers, prod hardening) |
| **API contract tests in CI** | **GitHub Actions**: sync + **Spectral** (`.spectral.yaml`) on prod spec + on **PR** `oasdiff breaking` vs merge base | Stricter Spectral rules or extra policies — `docs/api/versioning.md` |
| **API versioning** | Documented in [`docs/api/versioning.md`](../api/versioning.md); paths may stay unversioned until you publish a stable external API | Add `/api/v1/...` (or header strategy) when promoting a consumer-facing contract; sunset policy as needed |
| **Idempotency & events** | Synchronous HTTP only (ADR 0001) | Idempotency keys for mutating APIs; **outbox**, brokers, consumers — see **`docs/roadmap/event-driven-orchestration.md`** |
| **Event-driven orchestration** | No broker, no outbox, no workflow engine in repo | Phased plan: infra → outbox → consumers → async observability → optional workflow ADR ([roadmap doc](../roadmap/event-driven-orchestration.md)) |
| **Multitenancy** | Single-tenant data model | Tenant resolution strategy (JWT claim, header); RLS or discriminator column pattern + migration discipline |
| **Compliance** | No control framework mapping | Traceability: logging/retention, PII handling notes, optional SOC2/GDPR checklist appendix |
| **Rate limiting / abuse protection** | None in app | Choose: Quarkus filter + Bucket4j, or document **API Gateway** (Kong, Envoy, AWS API GW) as owner of limits |

## Phased implementation plan

### Phase A — CI and contract safety (high leverage, low coupling)

**Status (template): implemented** via GitHub Actions + `docs/api/versioning.md`.

1. **CI** runs `./mvnw -B -DskipTests package -Dquarkus.profile=prod` and fails if **committed** `openapi/openapi.yaml` ≠ **generated** output (drift gate).
2. **Spectral** lints `openapi/openapi.yaml` (rules in `.spectral.yaml`; CI uses `--fail-severity error` so warnings do not fail the job unless promoted to errors).
3. On **pull requests**, **`oasdiff breaking`** compares the merge base’s `openapi/openapi.yaml` to the PR’s generated spec so **breaking** API changes fail the job unless the contract is intentionally updated.
4. **Versioning / prod regen** process: [`docs/api/versioning.md`](../api/versioning.md) — after API changes run **`make openapi-generate-prod`** (or `make openapi-check-prod`) before commit. Local parity: `make openapi-check-sync`, `make openapi-spectral`.

*Optional backlog:* rename artifact to `openapi-ci.yaml` if you prefer a dedicated filename (behavior is unchanged).

### Phase B — Security baseline (application + edge)

1. **Threat model lite (STRIDE one-pager)** — documented: [`docs/security/threat-model-lite.md`](../security/threat-model-lite.md).
2. **Optional OIDC (`secured` profile)** — `quarkus-oidc` + JWT bearer validation; `GET /api/secured/me`; see [`docs/security/oidc-secured-profile.md`](../security/oidc-secured-profile.md). *(Optional backlog: integration tests with Testcontainers Keycloak.)*
3. **Rate limiting**: enforce at **API Gateway (Envoy)**; keep dev/test unblocked. Reference: `infra-bootstrap/docs/gateway/envoy-rate-limiting.md`.
4. Harden defaults: **security headers** (stage/prod), **CORS template** (service-side off by default; owned by gateway), and stricter defaults in `%prod`/`%stage`.
5. **AuthZ example**: role-protected business endpoint (`/api/admin/ping`) + tests (no Keycloak required).

### Phase C — Reliability patterns for scale

1. **Idempotency-Key** support for `POST`/`PUT` where appropriate; document deduplication storage (TTL).
2. If messaging is introduced: **outbox pattern** ADR + Flyway table; at-least-once + idempotent consumers.

### Phase G — Event-driven integration and orchestration (detailed checklist)

Full gap inventory (messaging infra, outbox, sagas/workflow engines, async observability, testing, ops) and suggested milestones **G1–G5** are maintained in:

**→ [`docs/roadmap/event-driven-orchestration.md`](../roadmap/event-driven-orchestration.md)**

Update that file when closing gaps; keep this ADR section as the **index** into Phase G.

### Phase D — Multitenancy (only if product needs it)

1. ADR: tenant identifier source; DB strategy (RLS vs `tenant_id` column); indexes and migration rules.
2. Integration tests with two tenants; forbid cross-tenant reads in repository layer.

### Phase E — Compliance packaging

1. Map controls to features (audit log fields, retention in Loki, encryption at rest delegated to DB/cloud).
2. Optional `docs/compliance/` checklist (GDPR/SOC2 excerpts as **non-binding** guidance for adopters).

### Phase F — SRE alerting (close the loop with ADR 0002)

1. Provision **Grafana alert rules** or **Prometheus `rule_files`** matching SLO targets (availability, latency).
2. Wire notification channels (placeholder) or document integration with on-call tool.

## Consequences

- **Positive:** Clear **backlog** for maintainers and forks; gaps are explicit, not hidden.
- **Positive:** Terminology (**rate limiting** vs generic “API limits”) is standardized.
- **Negative:** The template remains **lighter** than a full enterprise platform until phases land — by design, with a path forward.

## Related documents

- ADR 0001 — Gold Template concept  
- ADR 0002 — SRE / production readiness (SLO **targets**; alerting implementation tracked in Phase F above)  
- ADR 0007 — Catalog hexagonal slice  
- `docs/RUNBOOK.md` — incident response  
- `docs/roadmap/README.md` — roadmap index  
- `docs/api/versioning.md` — API versioning and contract process (Phase A)  
- `.spectral.yaml` — Spectral rules for OpenAPI lint (CI + `make openapi-spectral`)  
- `docs/security/threat-model-lite.md` — STRIDE threat model lite (Phase B step 1)  
- `docs/roadmap/event-driven-orchestration.md` — Phase G detail (event-driven & orchestration backlog)  
