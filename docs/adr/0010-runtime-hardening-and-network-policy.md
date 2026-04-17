# ADR 0010: Runtime Hardening and Kubernetes Manifest Security Baseline

**Date:** 2026-04-17
**Status:** Accepted

## Context

The Helm chart shipped with the template deploys a Quarkus workload with a permissive default runtime profile:

- a `templates/ingress.yaml` still exists even though platform routing has moved to Gateway API (Envoy Gateway) under `infra-bootstrap/k8s/gateway/routes/`;
- the `Deployment` has no `securityContext` and no capability drop, so containers run as root with a writable root filesystem and all default Linux capabilities;
- there is no `NetworkPolicy` — every pod can reach every other pod in the namespace;
- there is no `PodDisruptionBudget` — a single voluntary drain can evict the only replica;
- `application.properties` writes logs to both stdout **and** `logs/quarkus.log`, which makes `readOnlyRootFilesystem: true` impossible without an extra volume and duplicates every line in two destinations.

At the same time, the local development target is k3d, whose default CNI is **Flannel**. Flannel does not enforce `NetworkPolicy`: the manifests exist, `kubectl get networkpolicy` returns rows, but no traffic is ever blocked. Turning `NetworkPolicy` on in local/dev creates a **false sense of security** — developers believe they have segmentation they do not have, and drift between local and stage is masked rather than revealed.

This ADR records the hardening baseline for every service cloned from the template and how the baseline behaves across environments whose CNIs differ.

## Decision

### 1. Delete the dead `Ingress` template

`deploy/helm/templates/ingress.yaml` and the `ingress:` block in `values.yaml` / `values-stage.yaml` are removed. Routing is owned by `infra-bootstrap/k8s/gateway/routes/*.yaml` (Envoy Gateway `HTTPRoute`). The chart exposes only a `Service`; the route is attached at the platform level.

Rationale:

- Two sources of truth (chart Ingress + platform HTTPRoute) diverge the moment one is touched without the other. The platform source wins because it concentrates cross-service policy (rate limiting, auth filters, TLS termination).
- `Ingress` is a legacy API at the platform level; new Kubernetes features land on Gateway API. Carrying `Ingress` in every service chart slows the migration.
- README gains an “Exposing the service” section that points to `infra-bootstrap/k8s/gateway/routes/<service>.yaml`.

### 2. Pod and container `securityContext`

Every workload in the chart sets:

```yaml
securityContext:          # pod-level
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  seccompProfile:
    type: RuntimeDefault
containers:
- securityContext:        # container-level
    allowPrivilegeEscalation: false
    readOnlyRootFilesystem: true
    capabilities:
      drop: [ALL]
```

This matches the **restricted** profile of the Kubernetes Pod Security Standards (PSS). All three conditions of the restricted profile (non-root, no privilege escalation, dropped caps) are satisfied without per-service overrides.

### 3. Writable `emptyDir` volumes for paths Quarkus needs

`readOnlyRootFilesystem: true` breaks two things out of the box:

- `/tmp` — Quarkus may write temporary files (multipart uploads, Hibernate second-level cache overflow, OpenAPI schema store when configured that way).
- `/deployments` — unused at runtime for `native-micro`, but mounted by a few JVM paths.

The chart mounts `emptyDir`-backed volumes at `/tmp` (and anywhere else proven necessary by a failing image) rather than turning `readOnlyRootFilesystem` off. If a later feature needs a writable path, the reviewer adds one `emptyDir` entry; the surface stays small and explicit.

### 4. `PodDisruptionBudget` with `minAvailable: 1`

`minAvailable: 1` prevents voluntary disruptions (node drains, cluster autoscaler scale-down, rolling Kubelet upgrades) from bringing the deployment to zero replicas. For single-replica deployments, this means voluntary evictions block until another replica exists; for multi-replica deployments, it still guarantees one pod is up during disruption.

Involuntary disruptions (node failure, kernel panic) are not covered by PDB; they are handled by the HPA and the replica count policy. PDB is narrowly scoped on purpose.

### 5. Logs: stdout JSON only in prod

`application.properties` is updated so that:

- `%prod.quarkus.log.file.enabled=false` — no file appender in prod (compatible with `readOnlyRootFilesystem: true`).
- `%prod.quarkus.log.console.json.enabled=true` is retained — single source of truth, collected by Grafana Alloy from container stdout and shipped to Loki per ADR 0006.
- Dev / test profiles keep the file appender for interactive debugging (no container security constraints apply).

No custom log directory volumes. No duplicate streams. The logging stack described in ADR 0003 and ADR 0006 remains unchanged on the collection side; this is purely a producer-side change.

### 6. `NetworkPolicy` — env-split strategy (`enabled=false` locally, `enabled=true` in stage/prod)

`values.yaml` (the file inherited by `k3d` / local runs):

```yaml
networkPolicy:
  enabled: false   # k3d uses Flannel — NetworkPolicy is not enforced
```

`values-stage.yaml` and `values-prod.yaml`:

```yaml
networkPolicy:
  enabled: true
```

When rendered, the `NetworkPolicy`:

- **ingress** — allow from `gateway-system` (Envoy Gateway selects the service) and `monitoring` (Prometheus scrapes `/q/metrics`);
- **egress** — allow PostgreSQL (`5432` to the database subset), OTLP collector (`4317`), OIDC issuer (Keycloak, `443`/`8443`), DNS (`53` UDP+TCP to `kube-system`), and nothing else;
- default deny implied by having both `policyTypes: [Ingress, Egress]` set and any rule block present.

Rationale for the split:

- **Correctness over theatre.** Turning the flag on with Flannel produces a rendered manifest that blocks nothing. Developers then accumulate misconfigurations that “work locally, fail in stage.” The dishonest option is to hide the flag; the honest option — taken here — is to admit the CNI does not enforce and point at the follow-up track that fixes it.
- **Stage/prod have enforcing CNIs.** The platform plan is Cilium in stage/prod (tracked below). The rendered manifest is the same in all environments; only the enforcement differs.
- **CI gate.** A CI step (added in Wave 2 Block A) runs `helm template -f values-prod.yaml` and fails the build if zero `kind: NetworkPolicy` resources are emitted. This prevents a values-file edit from silently disabling segmentation in prod.

### 7. CNI follow-up — migrate k3d to Cilium (tracked in `infra-bootstrap`)

A separate task in `infra-bootstrap` will:

1. Add a `k3d cluster create` profile that disables the default Flannel and installs Cilium via its Helm chart.
2. Flip `networkPolicy.enabled=true` in `values.yaml` of the template once the local cluster enforces.
3. Document the migration in `infra-bootstrap/docs/cni/cilium-migration.md` and delete this ADR's section 7 once complete.

This keeps ADR 0010 pure to the **application chart** surface while acknowledging the platform-side work that closes the local-vs-stage gap.

## Consequences

- **Positive — PSS restricted by default.** Any cluster with the Pod Security Admission controller set to `restricted` accepts this chart without overrides; any cluster set to `baseline` is strictly more permissive than the chart requires.
- **Positive — one routing source of truth.** Gateway API routes in `infra-bootstrap` carry all cross-service policy; application chart no longer duplicates Ingress resources that would drift.
- **Positive — explicit writable surface.** `emptyDir` at `/tmp` (and only `/tmp` for now) makes it easy to audit what the container can write. Reviewers immediately notice if a feature needs a new writable path.
- **Positive — honest NetworkPolicy posture.** Rendered manifests match reality in every environment; the gap between local and stage is visible and tracked rather than concealed.
- **Negative — breaking change for existing forks.** Removing the Ingress template means any consumer relying on the chart-owned Ingress must switch to Gateway API or re-add the manifest locally. Documented in the README upgrade note.
- **Negative — `readOnlyRootFilesystem` surprises on feature work.** Adding a dependency that writes to disk (Lucene index, Bouncy Castle key cache, etc.) will fail at runtime. Mitigation: the CI smoke test starts the pod; any write-to-read-only-fs regression is caught before merge.
- **Negative — local k3d has no network isolation until Cilium lands.** Accepted trade-off, scoped and tracked.

## Related documents

- ADR 0002 — SRE / production readiness
- ADR 0003 — Observability and monitoring stack
- ADR 0006 — Centralized logging
- ADR 0008 — Platform evolution roadmap (Phase B: security baseline)
- `infra-bootstrap/k8s/gateway/routes/` — platform-owned HTTPRoutes (replaces the deleted chart Ingress)
- `infra-bootstrap/docs/cni/cilium-migration.md` *(to be created)* — k3d → Cilium migration task
- [Kubernetes Pod Security Standards — restricted profile](https://kubernetes.io/docs/concepts/security/pod-security-standards/#restricted)
- [Flannel does not enforce NetworkPolicy — upstream note](https://github.com/flannel-io/flannel#networkpolicy)
