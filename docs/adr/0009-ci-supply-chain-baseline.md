# ADR 0009: CI / Supply-Chain Baseline for the Gold Template

**Date:** 2026-04-17
**Status:** Accepted

## Context

Before this ADR the repository had exactly one application-scope CI workflow — `openapi-contract.yml` (Phase A of ADR 0008) — and no build/test/security pipeline. The remaining jobs lived only in developers' heads: "run `./mvnw verify` locally, build a Docker image on the k3d host, trust the base image." That is an acceptable stance for a demo, but it contradicts the "Gold Template" promise: **anyone cloning this repository should inherit a production-ready pipeline on day one**.

Two further forces pushed a decision:

1. **`infra-bootstrap`** already carries its own CI for platform artefacts (helm lint, kubeconform, conftest). There is real overlap risk if application and infrastructure CI grow independently.
2. The team wants SonarQube self-hosted, Trivy image scanning, CycloneDX SBOMs, and Dependabot — all standard supply-chain hygiene — but does not yet have the SonarQube server running. The pipeline must **degrade gracefully** instead of blocking commits until that server exists.

This ADR records the resulting decisions so that future services cloned from this template inherit them verbatim.

## Decision

### 1. Ownership of application CI (app-local, not infra-local)

Application CI lives **inside the application repository** (`.github/workflows/ci.yaml`). It does **not** migrate into `infra-bootstrap`.

Rationale:

- **Triggers belong to the code:** application CI must run on every PR/push to the application repo. Triggers in `infra-bootstrap` cannot observe those events.
- **Cycle time:** app engineers iterate on PRs minute-by-minute; infra CI batches weekly. Mixing them would couple one team's latency to the other's review cadence.
- **Ownership and on-call:** the team that merges code is the team that must unblock a red pipeline. Moving CI to another repo splits ownership.

### 2. Reuse strategy: **hybrid via GitHub reusable workflows**

Long-term, generic CI building blocks (setup-java, mvn verify, Trivy scan, SonarQube invocation) will be defined **once** in `infra-bootstrap/.github/workflows/reusable-java-ms-ci.yaml` and consumed by every service via `workflow_call`. Service-specific steps (e.g. the existing OpenAPI contract gate, domain ArchUnit tests) stay local to each application.

Transition plan:

1. **Now (this ADR):** both orchestration and step bodies live in `quarkus-ms-gold-template/.github/workflows/ci.yaml`. No external dependency — anyone cloning the template gets a working pipeline immediately.
2. **When a second service joins the platform:** extract the repeated steps into a reusable workflow in `infra-bootstrap`. Replace the body in each application repo with `uses: <org>/infra-bootstrap/.github/workflows/reusable-java-ms-ci.yaml@v1`.

This mirrors the hybrid model already chosen for alerts in ADR 0008 Phase F: **platform-wide concerns centralised, service-specific concerns local**.

### 3. Workflow decomposition

| Workflow | Scope | Trigger |
|----------|-------|---------|
| `openapi-contract.yml` *(existing, ADR 0008 Phase A)* | OpenAPI sync + Spectral + `oasdiff breaking` | PR, push to `main` |
| `ci.yaml` *(new — this ADR)* | Test + coverage + JVM image build + Trivy + SBOM + Sonar | PR, push to `main` |
| `release.yaml` *(new — this ADR)* | Native image build + scan + GitHub Release attachments | Tag `v*.*.*`, manual dispatch |

Native image builds take 10–15 minutes on GitHub-hosted runners. Running them on every PR would dominate CI time without catching many additional bugs (JVM tests already cover the application behaviour; native regressions are bounded and tag-gated releases are acceptable safety nets).

### 4. Chosen tools and versions

| Concern | Tool | Version | Rationale |
|---------|------|---------|-----------|
| **Coverage** | `jacoco-maven-plugin` | 0.8.14 | First version with **official Java 25 class-file support** (major 69). Earlier versions fail with `Unsupported class file major version 69`. |
| **SBOM** | `cyclonedx-maven-plugin` | 2.9.1 | Native Maven integration — builds SBOM from the real dependency graph, not a heuristic image scan. More accurate for Java libraries than anything derived from the container layer. CycloneDX format chosen over SPDX for tooling maturity in the JVM ecosystem. |
| **Image scan** | `aquasecurity/trivy-action` | 0.28.0 | OS + library + secret + misconfig scanners in one invocation. Two-pass strategy: SARIF upload for the Security tab (informational); fail-fast table scan with `ignore-unfixed=true, severity=HIGH,CRITICAL` as the PR gate. |
| **SAST** | SonarQube Community Edition (self-hosted) | — | See [`docs/infra/sonarqube-setup.md`](../infra/sonarqube-setup.md). Conditional activation — see decision 5 below. |
| **Dependency bot** | Dependabot | — | Three ecosystems: `maven`, `github-actions`, `docker`. Grouped PRs (`quarkus`, `opentelemetry`, `test-tooling`, `build-plugins`) keep review noise manageable. |

The CycloneDX plugin requires `<skipNotDeployed>false</skipNotDeployed>` — the plugin's default heuristic skips modules whose `maven-deploy-plugin` has `skip=true`, and Quarkus `packaging=quarkus` modules happen to trip it. Without the override the SBOM silently never gets written.

### 5. Graceful degradation for not-yet-provisioned SonarQube

The SonarQube self-hosted server does not yet exist. The `sonar` job in `ci.yaml` is guarded by:

```yaml
if: ${{ vars.SONAR_HOST_URL != '' }}
```

`vars.SONAR_HOST_URL` is a non-secret repository/organisation variable. While unset, the job is **skipped** — skipped jobs count as success in the aggregation gate, so PRs do not block on missing Sonar infrastructure. Once the server is live, defining `SONAR_HOST_URL` and `SONAR_TOKEN` in the repository settings automatically activates SonarQube analysis on the next CI run; no workflow edit is required.

The same mechanism applies to every service cloned from this template — onboarding is two GitHub settings entries.

### 6. Branch protection via an aggregation gate

Branch protection references **one** required status check — `ci-passed` — not the individual job names. `ci-passed`:

- depends on every other job via `needs:`,
- is `if: always()`,
- fails when any dependency failed or was cancelled,
- passes when every dependency succeeded **or was legitimately skipped** (Sonar when `SONAR_HOST_URL` is unset).

Adding or renaming jobs no longer requires reconfiguring branch protection rules.

### 7. SonarQube server provisioning (deferred to `infra-bootstrap`)

The server itself is a platform concern, not an application concern. Its deployment spec — Helm chart wrapper, SealedSecrets per environment, ArgoCD Application, HTTPRoute via Envoy Gateway, Keycloak OIDC client, Quality Gate baseline — is captured as a task for the infrastructure repository in [`docs/infra/sonarqube-setup.md`](../infra/sonarqube-setup.md). The application side is ready ahead of time; activation is purely a matter of flipping the two GitHub variables once the infra PR merges.

### 8. Image registry — explicit deferral

Image push is currently a dry run (`push: false` in both `ci.yaml` and `release.yaml`). The target registry is ghcr.io in principle, but the decision is deferred until the first environment deploys the template. `TODO(registry):` markers in the workflows flag the exact lines that change. This keeps the pipeline CI-clean while the registry conversation is still open without committing credentials to a disabled path.

## Consequences

- **Positive — clone-and-go.** A new service forked from this template inherits a working pipeline immediately, even before the platform is fully provisioned. Two GitHub variables (`SONAR_HOST_URL`, `SONAR_TOKEN`) complete the setup.
- **Positive — supply-chain visibility.** Every merged commit now has: CVE-scanned image (SARIF in Security tab), machine-readable SBOM attached as an artefact, JaCoCo coverage report, and — once Sonar is up — Quality Gate verdicts including security hotspots, duplications, and coverage thresholds.
- **Positive — decoupled cadence.** Application PRs no longer wait on infrastructure changes (and vice versa). Reusable workflows will later deduplicate YAML without re-introducing the coupling.
- **Negative — initial YAML duplication.** Until a second service appears, the CI YAML in this template is the only copy. Extraction to `infra-bootstrap/reusable-java-ms-ci.yaml` is deliberately deferred — premature centralisation of a single consumer would cost more than it saves.
- **Negative — Sonar coverage lag.** Between this ADR landing and the SonarQube server going live, Sonar warnings are not visible. The workflow is structured so that activation is a pure configuration change with no code edits required.
- **Negative — Java 25 toolchain coupling.** JaCoCo 0.8.14 was released in October 2025; adopters on older JaCoCo versions must upgrade. Documented above with a pointer to the upstream changelog entry.

## Related documents

- ADR 0001 — Gold Template concept
- ADR 0002 — SRE / production readiness
- ADR 0007 — Catalog bounded context (hexagonal slice + ArchUnit)
- ADR 0008 — Platform evolution roadmap (Phase A: OpenAPI contract CI — predecessor to this ADR)
- [`docs/infra/sonarqube-setup.md`](../infra/sonarqube-setup.md) — SonarQube self-hosted deployment spec for `infra-bootstrap`
- [`.github/workflows/ci.yaml`](../../.github/workflows/ci.yaml) — application CI pipeline
- [`.github/workflows/release.yaml`](../../.github/workflows/release.yaml) — native release pipeline
- [`.github/dependabot.yml`](../../.github/dependabot.yml) — dependency update policy
- [`sonar-project.properties`](../../sonar-project.properties) — SonarQube project configuration
- [JaCoCo 0.8.14 changelog](https://www.jacoco.org/jacoco/trunk/doc/changes.html) — Java 25 official support note
