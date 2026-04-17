# SonarQube self-hosted — setup task for `infra-bootstrap`

This document is the **task spec** for bringing a self-hosted SonarQube Community Edition online in the shared platform. The implementation lives in the sibling `infra-bootstrap/` repository; this file records **what needs to happen** so that the conditional `sonar` job in [`ci.yaml`](../../.github/workflows/ci.yaml) activates for the gold-template microservice and all future services cloned from it.

---

## Why SonarQube, scope of this task

Sonar covers three classes of defect that neither CVE scanners (Trivy) nor style linters catch:

1. **Semantic bugs** — null-dereferences, resource leaks, broken `equals`/`hashCode`, concurrency hazards.
2. **Security hotspots** — hardcoded secrets, weak crypto, unvalidated input sinks (with manual review flow, not auto-block).
3. **Maintainability signals** — code smells, duplication, cognitive complexity, test coverage thresholds enforced via Quality Gate.

Trivy and SonarQube are complementary: Trivy = "do my dependencies have known CVEs"; Sonar = "is my own code correct and maintainable".

**Scope of this task:** one SonarQube Community server serving **all platform services** (multi-project). Single instance, no HA yet.

---

## Architecture decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Edition** | Community Edition (free) | Developer/Enterprise features (branch analysis, Pull-Request decoration) are not blockers for a template. Upgrade path is a license-key swap, no migration. |
| **Deployment** | Helm chart (Bitnami) | Bitnami's `sonarqube` chart is well-maintained, integrates with PostgreSQL subchart cleanly. Alternative: official SonarSource chart (also viable — decide at PR review time). |
| **Database** | Dedicated PostgreSQL via chart dependency | Sonar requires its own DB; sharing with Keycloak is explicitly unsupported. Subchart keeps lifecycle atomic. |
| **Storage** | PVC, `ReadWriteOnce`, 20 Gi | Sonar stores plugins, ES indices, analysis history on disk. 20 Gi covers ~2 years for 10 services at weekly scan cadence. |
| **Auth** | OIDC via Keycloak (reuse platform IdP) | Avoids another credential store. SonarQube has native OIDC plugin. Admin bootstrap still uses local account — protected by SealedSecret. |
| **Exposure** | HTTPRoute on Envoy Gateway, hostname `sonar.<domain>` | Mirrors pattern of Grafana/Keycloak/ArgoCD already in `infra-bootstrap`. |
| **Backups** | PVC snapshot via the cluster's volumesnapshot class (out of scope for first PR) | Follow-up; noted in the chart's `NOTES.txt`. |

---

## Deliverables in `infra-bootstrap`

The following artefacts should be added by a PR to `infra-bootstrap`. Naming follows existing conventions (see `helm/keycloak*`, `argocd/applications/<env>/`, `secrets/<env>/`).

### 1. Helm chart wrapper

```
infra-bootstrap/
  helm/
    sonarqube/
      Chart.yaml           # declares dependency on upstream chart
      values.yaml          # shared defaults
      values-test.yaml
      values-stage.yaml
      values-prod.yaml
```

Key values (illustrative — adjust to chart):

```yaml
sonarqube:
  edition: community
  monitoringPasscode: ""   # set via SealedSecret env-merge
  persistence:
    enabled: true
    size: 20Gi
  resources:
    limits:    { cpu: "2",    memory: "4Gi" }
    requests:  { cpu: "500m", memory: "2Gi" }
  sonarProperties:
    sonar.forceAuthentication: "true"
    sonar.auth.oidc.enabled: "true"
    sonar.auth.oidc.issuerUri: "https://keycloak.<domain>/realms/gold"
    sonar.auth.oidc.clientId.secured: "sonarqube"
postgresql:
  enabled: true
  auth:
    existingSecret: sonarqube-db-auth
    secretKeys:
      adminPasswordKey: postgres-password
      userPasswordKey: password
  primary:
    persistence:
      size: 10Gi
```

### 2. SealedSecrets

```
infra-bootstrap/secrets/
  _templates/
    sonarqube-db-auth.template.yaml
    sonarqube-admin-auth.template.yaml
  test/
    sonarqube-db-auth.sealedsecret.yaml
    sonarqube-admin-auth.sealedsecret.yaml
  stage/  # same pair
  prod/   # same pair
```

- `sonarqube-db-auth`: PostgreSQL password for Sonar schema.
- `sonarqube-admin-auth`: initial bootstrap admin password (changed after first login).

### 3. ArgoCD Application

```
infra-bootstrap/argocd/applications/<env>/
  20-sonarqube.yaml        # Application pointing at helm/sonarqube/, values-<env>.yaml
```

Numbered `20-` so it installs after `00`–`15` (secrets, gateway, observability, keycloak). Sonar depends on Keycloak realm being reachable for OIDC, but not at install time — OIDC is verified lazily on first login.

### 4. HTTPRoute

```
infra-bootstrap/k8s/gateway/routes/
  sonarqube.yaml           # HTTPRoute + ReferenceGrant if cross-namespace
```

### 5. Keycloak realm client

Add a client `sonarqube` to `infra-bootstrap/keycloak/realm-gold-<env>.json`:

- Client type: confidential.
- Valid redirect URIs: `https://sonar.<domain>/oauth2/callback/oidc`.
- Client scopes: `openid`, `profile`, `email`.
- Client secret: stored in SealedSecret, injected into SonarQube via `sonar.auth.oidc.clientSecret.secured`.

### 6. CI policy update (optional, light-touch)

`infra-bootstrap/policies/helm.rego`: ensure the Sonar Deployment has the same pod-security baseline as other platform services (`runAsNonRoot`, no privilege escalation, limits set). No new policy needed if existing ones are selector-based.

---

## Post-deploy bootstrap (one-time, manual)

1. Open `https://sonar.<domain>`, sign in with the `admin` account from `sonarqube-admin-auth`.
2. Force password change on first login (Sonar prompts automatically).
3. Set instance public URL in **Administration → General → Server base URL**.
4. Enable the **OpenID Connect** plugin and link it to the existing Keycloak client (values from `sonar.auth.oidc.*` are prefilled, but OIDC must be enabled once from the UI to validate).
5. Create an **Analysis Token** for CI:
   - **Administration → Security → Users → Tokens** (or use a dedicated service user).
   - Scope: `Execute Analysis`.
   - Copy the token value (shown once).
6. Create the project `quarkus-ms-gold-template`:
   - **Projects → Create Project → Manually**.
   - Project key: `quarkus-ms-gold-template` (must match `sonar.projectKey` in [`sonar-project.properties`](../../sonar-project.properties)).
7. Customise the default Quality Gate — recommended starting thresholds for a new template:
   - **Coverage on New Code** ≥ 80 %.
   - **Duplicated Lines on New Code** < 3 %.
   - **Security Hotspots Reviewed on New Code** = 100 %.
   - **Maintainability Rating on New Code** ≤ A.

---

## Wiring the microservice CI to the running Sonar server

Once the infra PR is merged and Sonar is reachable, the microservice side needs two pieces of metadata in its GitHub repository — nothing else changes in code:

| Type | Name | Value | Where to set |
|------|------|-------|--------------|
| Variable | `SONAR_HOST_URL` | `https://sonar.<domain>` | Settings → Secrets and variables → Actions → **Variables** |
| Secret | `SONAR_TOKEN` | analysis token from step 5 above | Settings → Secrets and variables → Actions → **Secrets** |

The `sonar` job in [`ci.yaml`](../../.github/workflows/ci.yaml) guards itself with `if: vars.SONAR_HOST_URL != ''` — it activates automatically on the next CI run after the variable is set. No workflow edit required.

For **all future services** cloned from this template: the same two-value setup in their repo is enough. The Sonar project key comes from `sonar-project.properties` and should be renamed to match the new service.

---

## Follow-ups (not blocking first deployment)

- [ ] PVC snapshot cron for backups.
- [ ] Prometheus scrape of Sonar's own metrics endpoint (`/api/monitoring/metrics`, requires `monitoringPasscode`).
- [ ] Sonar's Branch Analysis — needs Developer Edition license if ever upgraded.
- [ ] Extract a platform-wide Quality Gate profile and reference it from every service.
- [ ] SonarLint IDE config committed into the template so local and CI warnings stay in sync.
