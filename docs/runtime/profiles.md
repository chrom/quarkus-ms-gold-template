# Profiles (dev/test/stage/prod/secured)

This template uses **Quarkus profiles** to separate developer experience from enterprise runtime constraints.

## Default profiles

- **`dev`**: local development. Minimal external dependencies. **No gateway policies** (rate limiting), OIDC is **off** by default.
- **`test`**: deterministic test runs. No external services required. Keep security features that break tests **off**.
- **`stage`**: production-like runtime. Intended to run **behind a gateway**. OIDC is **on**, security headers are **on**.
- **`prod`**: production runtime. Intended to run **behind a gateway**. OIDC is **on**, security headers are **on**, stricter defaults.

## Optional profile: `secured`

`secured` exists as a **developer-only switch** to validate OIDC locally without making `dev` depend on Keycloak.

- Enable: `./mvnw quarkus:dev -Dquarkus.profile=dev,secured` (or `make dev-secured`)
- Details: `docs/security/oidc-secured-profile.md`

## Responsibilities (where policies live)

- **Gateway (Envoy, recommended for stage/prod)**:
  - Rate limiting / quotas / abuse protection
  - Browser-facing CORS policy (when applicable)
  - TLS termination / HSTS
- **Service (this template)**:
  - Minimal security headers baseline in `stage`/`prod` (`SecurityHeadersFilter`)
  - Business authorization (roles/permissions) inside endpoints

