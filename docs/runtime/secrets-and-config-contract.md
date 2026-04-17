# Secrets & config contract (template → platform)

This document defines the **environment variables** the template expects in `stage`/`prod`.

## OIDC (required in stage/prod)

- **`QUARKUS_OIDC_AUTH_SERVER_URL`**: issuer URL, e.g. `http://keycloak.keycloak.svc.cluster.local:8180/realms/gold-dev`
- **`QUARKUS_OIDC_CLIENT_ID`**: resource-server client id (audience), e.g. `quarkus-ms-gold-template-api`
- **`QUARKUS_OIDC_CLIENT_SECRET`**: optional, depends on realm/client settings

## Database (Kubernetes)

For stage/prod you typically use an **external DB** and inject:

- `QUARKUS_DATASOURCE_JDBC_URL`
- `QUARKUS_DATASOURCE_USERNAME`
- `QUARKUS_DATASOURCE_PASSWORD`

Avoid committing real values. Use **SealedSecrets** (GitOps) or **ExternalSecrets** (cloud secret managers).

## Helm chart posture

- `deploy/helm/values.yaml` is optimized for **k3d dev/stage-like** cluster demos.
- `deploy/helm/values-prod.yaml` is intentionally **strict**: avoid placeholder defaults; require explicit OIDC and secret refs.

