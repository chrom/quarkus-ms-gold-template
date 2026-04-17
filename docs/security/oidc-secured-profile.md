# Optional OIDC profile (`secured`)

The template ships **without** requiring Keycloak: `quarkus.oidc.enabled=false` by default.

To validate **Bearer JWTs** against an OIDC issuer (e.g. Keycloak from sibling [`infra-bootstrap`](../../../infra-bootstrap/)):

1. **Activate profiles** `dev` **and** `secured` (developer-only OIDC validation switch):

   ```bash
   ./mvnw quarkus:dev -Dquarkus.profile=dev,secured
   ```

   or:

   ```bash
   export QUARKUS_PROFILE=dev,secured
   ./mvnw quarkus:dev
   ```

   Makefile: `make dev-secured`.

2. **Set the issuer** (must match your realm):

   | Variable | Purpose |
   |----------|---------|
   | `QUARKUS_OIDC_AUTH_SERVER_URL` | Issuer base URL, e.g. `http://localhost:8180/realms/gold-dev` |
   | `QUARKUS_OIDC_CLIENT_ID` | Resource-server client id (default `quarkus-app`) |
   | `QUARKUS_OIDC_CLIENT_SECRET` | Optional; often empty for pure JWKS validation |

   Defaults are in `application.properties` under `%secured`.

3. **Endpoint** `GET /api/secured/me` is registered **only** when the `secured` build profile is active (`@IfBuildProfile("secured")`). It returns JSON `{ "subject": "...", "roles": [...] }` for a valid access token.

> Note: In this template **`stage`** and **`prod`** profiles have OIDC **enabled by default** (they assume a gateway + issuer).
> `secured` exists to keep day-to-day `dev` and `test` free of Keycloak requirements.

4. **Realm roles**: Keycloak `realm_access.roles` are mapped via `quarkus.oidc.roles.role-claim-path=realm_access/roles` for `@RolesAllowed` / authorization.

5. **OpenAPI**: The committed `openapi/openapi.yaml` is generated with the default **prod** profile (without `secured`), so `/api/secured/me` may be absent from the checked-in spec. Regenerate with `dev,secured` or `prod,secured` if you need those operations in the contract.

6. **Unauthenticated routes**: Greeting, catalog, etc. stay open unless you add `@Authenticated` / `@RolesAllowed` or HTTP permissions — this is intentional for the teaching template.

See [ADR 0008 Phase B](../adr/0008-platform-evolution-roadmap.md) and [threat-model-lite](threat-model-lite.md).
