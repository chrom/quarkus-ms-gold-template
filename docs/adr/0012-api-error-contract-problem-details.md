# ADR 0012: API Error Contract — RFC 7807 Problem Details

**Date:** 2026-04-18
**Status:** Accepted
**Supersedes:** the hybrid policy described in the original ADR 0008 draft (custom `ErrorResponse` + ad-hoc `Map.of("error", …)` bodies).

## Context

The gold template was accumulating three competing shapes of error responses, all emitted over `application/json`:

1. A record DTO `org.acme.exception.ErrorResponse { status, error, message, path, timestamp }` returned by `GlobalExceptionMapper` for any uncaught `Exception`.
2. Inline `Map.of("error", …)` bodies returned by `NotFoundExceptionMapper` and `CatalogNotFoundExceptionMapper` for 404s.
3. A `{error, requestId}` map returned by `UnhandledExceptionMapper` for uncaught `Throwable`s.

On the consumer side, this meant every generated client or SDK had to branch on the status code to know which shape to parse. The OpenAPI spec papered over the mismatch by describing 4xx/5xx responses generically, without attaching any media type or schema — so the document was technically accurate (the server does return `application/json`) but operationally useless (clients could not trust the field names).

The problem is not specific to this template; it is the default failure mode of any JAX-RS codebase that grows mappers organically. RFC 7807 (*Problem Details for HTTP APIs*, 2016) was written to standardise the shape: one media type (`application/problem+json`), one minimal body (`type`, `title`, `status`, `detail`, `instance`), and an explicit extension mechanism for domain-specific fields. Having a registered media type — rather than reusing `application/json` — lets both servers and CDNs route / log / cache errors separately from normal payloads, and tells negotiators like `Accept: application/json` versus `Accept: application/problem+json` apart at the HTTP layer.

Internally we already had the pieces to ship 7807 cleanly:

* `RequestIdFilter` injects a correlation id and stores it in MDC, so the `requestId` extension member can be populated for free.
* `DefaultResponseDescriptionOASFilter` proved that a `OASFilter` is the right place to normalise the OpenAPI document centrally.
* The `openapi-check-sync` gate (CI workflow `openapi-contract.yml`) already fails the build when the committed spec drifts from the codegen output.
* Spectral is already wired into the same workflow as a lint step.

Leaving the drift unresolved blocks two Wave 2 goals: (a) a Spectral rule that enforces the error media type — which cannot exist until every endpoint emits the same one, and (b) SDK generation for downstream consumers — which is currently hostile because of the branching problem above.

## Decision

### 1. One media type: `application/problem+json`

Every 4xx and 5xx response body uses `application/problem+json`. No other error media type is allowed.

### 2. One body schema: `ProblemDetail`

A single record `org.acme.rest.error.ProblemDetail` is the wire representation.  
Fields follow RFC 7807 §3.1 verbatim (`type`, `title`, `status`, `detail`, `instance`) and add three extension members allowed by §3.2:

* `requestId` — mirrors the `X-Request-Id` response header and the logging MDC, so a single id joins the log line with the client response.
* `timestamp` — UTC `Instant`, server wall-clock, serialised as ISO-8601.
* `errors` — an array of `{field, message}` pairs, populated only for bean-validation 400s.

The schema is registered once under `#/components/schemas/Problem` and referenced by every 4xx/5xx response.

### 3. One factory: `ProblemDetails`

All construction goes through `ProblemDetails.response(status, detail, uriInfo[, errors])`. No mapper builds a `ProblemDetail` by hand; no mapper sets the content type manually. This is how the contract stays uniform: there is exactly one place where the headers, `instance` URI, MDC extraction, and timestamp source live.

### 4. Four mappers, strictly specialised

| Mapper | Catches | Status semantics |
| --- | --- | --- |
| `UnhandledExceptionMapper` | `Throwable` | 500, generic `"Internal Server Error"` detail. Never leaks `exception.getMessage()`. |
| `WebApplicationExceptionMapper` | `WebApplicationException` | Preserves the original status and message. Covers 404/405/406/415 from JAX-RS plus 401/403 from security. |
| `ConstraintViolationExceptionMapper` | `jakarta.validation.ConstraintViolationException` | 400, `detail = "Validation failed"`, `errors` populated from the violation set. |
| `CatalogNotFoundExceptionMapper` | `CatalogNotFoundException` (domain) | 404, `detail = exception.getMessage()`. Kept separate so the domain stays JAX-RS-free and the mapper translates at the boundary. |

The legacy `GlobalExceptionMapper`, `ErrorResponse` record, and `NotFoundExceptionMapper` are deleted outright. Leaving them in as deprecated was considered and rejected: a deprecated mapper stays wired in by the CDI container and still competes with the new ones via JAX-RS's "most specific type wins" dispatch, which would silently reintroduce the legacy shape for uncaught `Exception` subclasses.

### 5. OpenAPI normalisation via `ProblemDetailsOASFilter`

An `OASFilter` runs at `BUILD`, `RUNTIME_STARTUP`, and `RUNTIME_PER_REQUEST` and:

* Ensures `#/components/schemas/Problem` exists (normally scanned from `ProblemDetail`; the filter adds a stub object schema as a defence against annotation-scan regressions).
* Rewrites every 4xx/5xx response in every operation to declare `application/problem+json` as its content type and `$ref` the shared `Problem` schema.

Running the filter at `BUILD` keeps the committed `openapi/openapi.yaml` identical to the live `/q/openapi` output, which the `openapi-check-sync` gate verifies.

### 6. Spectral enforcement

Three custom Spectral rules (in `.spectral.yaml`) guarantee the contract on the committed document:

* `problem-schema-defined` — `#/components/schemas/Problem` must exist.
* `problem-json-media-type-on-errors` — every 4xx/5xx response must list `application/problem+json`.
* `problem-schema-ref-on-errors` — that media type must `$ref` the shared schema.

All three are `severity: error`, i.e. they fail the `openapi-spectral` CI job.

### 7. ArchUnit enforcement for hexagonal placement

Because ADR 0007 asked for bounded-context slices while ADR 0008 still had `RecommendationService` sitting in a generic `org.acme.service` package, we took the opportunity to relocate it to `org.acme.catalog.adapter.out.recommendation` (its true owner — it backs the catalog `RecommendationPort`). Two ArchUnit rules live in `ArchitectureConventionsTest`:

* `noClassesInLegacyServicePackage` — nothing may reside in `org.acme.service..`.
* `recommendationServiceStaysInCatalogAdapter` — any class named `RecommendationService` must live in the catalog adapter package.

Co-locating this rule with the error-contract ADR is deliberate: both changes are about stopping drift between declared architecture and emitted code, and the two ArchUnit rules travel with the same commit that introduces the mappers.

## Consequences

### Positive

* Clients see one shape. Generated SDKs can expose a single `Problem` type instead of an `Error | NotFound | Validation | …` union.
* The correlation `requestId` is visible on both the log line and the response body, cutting incident triage time in half.
* The OpenAPI document is now *honest*: every documented error response tells the client exactly what it will receive, and Spectral gates keep it that way.
* The "hexagonal" slice rule in ADR 0007 has a concrete guardrail for the first time — no longer an aspiration.

### Negative

* Breaking change for any consumer that was parsing the old `{error, message, …}` shape. Communicated via a minor version bump on the OpenAPI document; downstream owners have to run their SDK regeneration.
* One extra `OASFilter` pass in the build. Measured impact on cold `quarkus:dev` start is negligible (<50 ms), but noted for completeness.
* `WebApplicationExceptionMapper` logs 5xx subtypes at WARN. Combined with whatever logged the exception at source, this can produce two log lines for the same event. Accepted: the doubled cost (cheap) is worth the guarantee that a thrown `InternalServerErrorException` is always visible on the on-call dashboard.

### Follow-ups (out of scope for Block C)

* Client SDK regeneration in the infra-bootstrap repo.
* Introduce domain-specific `type` URIs (e.g. `https://errors.example.com/catalog/not-found`) once the product catalogue stabilises — the template keeps `about:blank` for now, which is the RFC-specified default.

## Related documents

* ADR 0007 — Catalog hexagonal slice (motivates the `RecommendationService` relocation).
* ADR 0008 — Platform evolution roadmap (this ADR supersedes the informal error-handling plan it outlined).
* ADR 0011 — Integration tests on PostgreSQL via Testcontainers (the `ProblemDetailContractTest` runs against the same test profile).
* `.spectral.yaml` — enforcement rules.
* `openapi/openapi.yaml` — current contract snapshot.
