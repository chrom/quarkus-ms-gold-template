# ADR 0007: Catalog Bounded Context — Ports, Adapters, and Guardrails

**Date:** 2026-04-10  
**Status:** Accepted  

## Context

The Gold Template (ADR 0001) remains **pragmatic overall**: not every package is forced into a framework-agnostic core. Teams still asked for a **concrete, cloneable example** of how to structure a synchronous CRUD slice with clear boundaries, testable use cases, and alignment with REST/JSON/pagination without leaking transport concerns into the domain.

The **catalog** area (`org.acme.catalog`) was evolved toward a **hexagonal (ports & adapters)** style while keeping idiomatic Quarkus (CDI, Panache, MicroProfile) at the edges.

## Decision

### 1. Package layout

| Layer | Responsibility |
|-------|------------------|
| `domain` | Entities, value objects, domain exceptions, identifiers — **no** Jakarta/JAX-RS/JPA. |
| `application.port.out` | Outbound ports (e.g. `ProductRepositoryPort`, `CategoryRepositoryPort`, `RecommendationPort`). |
| `application.service` | Use cases orchestrating ports (`ProductApplicationService`, `CategoryApplicationService`). |
| `adapter.in.rest` | HTTP: JAX-RS resources, JSON DTOs, query params, mapping to domain/commands. |
| `adapter.out.persistence` | JPA/Panache implementing repository ports; entity ↔ domain mappers. |
| `adapter.out.recommendation` | Implements `RecommendationPort` by delegating to the existing simulated `RecommendationService` (fault tolerance kept there). |

REST resources depend on **application services** or **ports** only through those abstractions — not on `PanacheRepository` types directly (categories go through `CategoryApplicationService`; products through `ProductApplicationService` and `RecommendationPort` for recommendations).

### 2. REST, JSON, and pagination

- **Not** modeled in ports as “JSON” or “HTTP”.  
- **Pagination:** query parameters (`page`, `size`) are read in `ProductResource`, passed as `int` into the application service, and the outbound port returns a neutral `ProductRepositoryPort.ProductPage` (`List<Product>` + page metadata).  
- **JSON:** DTOs (`ProductResponse`, `ProductRequest`, `PagedResponse`) and `CatalogRestMapper` live in the **inbound REST adapter**; the domain and port signatures use domain types only.

### 3. Observability (metrics and traces)

- **Metrics (Micrometer):** `ProductApplicationService` registers `products.mutations` and uses `@Timed` for list; `RecommendationService` uses `@Counted` on the fallback path. These stay on the application/infrastructure beans that participate in use cases — not on port interfaces.  
- **Traces (OpenTelemetry):** spans are created via annotations such as `@WithSpan` on application and recommendation code. **Jaeger (and compatible UIs) display span durations in microseconds (µs)** by convention; exported OTLP data uses nanoseconds internally. This is UI presentation, not a separate codebase “unit” setting.

### 4. Architecture tests

- **ArchUnit** (`CatalogDomainArchitectureTest`) enforces that classes under `org.acme.catalog.domain..` depend only on `java..` and `org.acme.catalog.domain..`.  
- **Tooling:** `archunit-junit5` **1.4.1+** is required for **Java 25** class file support; older ArchUnit versions may import zero classes and silently void rules.

## Consequences

- **Positive:** Clear map for teams that want ports/adapters without a full rewrite of the template.  
- **Positive:** Domain rules can be unit-tested with fake ports; ArchUnit guards dependency direction.  
- **Negative:** More packages and indirection than a minimal layered tutorial; `ProductApplicationService` still carries Micrometer/MP Fault Tolerance/OpenTelemetry annotations (acceptable trade-off until further decomposition).  
- **Negative:** Other packages under `org.acme` (e.g. `RecommendationService` location) may remain “layered” until migrated — only the catalog tree follows this ADR strictly.

## Related documents

- ADR 0001 — Gold Template concept (baseline vs. catalog slice)  
- ADR 0003 — Observability stack (Jaeger, Prometheus, etc.)  
- `docs/observability/metrics-inventory.md` — metric names overview  
