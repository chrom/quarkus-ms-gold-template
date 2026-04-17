# API versioning (template policy)

This document complements [ADR 0008: Platform evolution roadmap](../adr/0008-platform-evolution-roadmap.md). The **contract of record** for HTTP APIs in this repository is the committed **`openapi/openapi.yaml`** (prod profile / SmallRye OpenAPI). If you track **`openapi/openapi.json`** (e.g. for Backstage), it is generated together with the YAML and must stay in sync.

## Prod OpenAPI — обов’язкова регенерація

Після **будь-якої** зміни REST-ресурсів, DTO або OpenAPI-анотацій, які впливають на публічний контракт:

1. Запусти **`make openapi-generate-prod`** (або повний **`make openapi-check-prod`**, який ще й валідує структуру через Docker).
2. Закоміть оновлені **`openapi/openapi.yaml`** і, якщо вони в репозиторії, **`openapi/openapi.json`** разом із кодом.
3. Перед комітом за бажанням: **`make openapi-spectral`** (Spectral lint) і **`make openapi-check-sync`** (як у CI).

Без кроку з **prod**-профілем згенерований артефакт **не** відображатиме фактичний контракт для production-збірки; CI на GitHub також збирає spec через **`./mvnw … -Dquarkus.profile=prod`**.

## Current state

- Paths are **not** yet prefixed with a version segment (e.g. resources may live under `/products` rather than `/api/v1/products`). That is acceptable for an internal or early-stage API.
- When you introduce a **published** API surface for external consumers, choose one strategy and document it in this file.

## Recommended options

| Strategy | When to use |
|----------|-------------|
| **URL prefix** (`/api/v1/...`) | Simple for clients and gateways; easy to route in ingress. |
| **`Accept-Version` or custom header** | When the same path must serve multiple representations. |
| **Subdomain or base path per major** (`/v1`, `/v2`) | Larger platforms; combine with routing docs. |

Pick **one** primary strategy per product; mixing without rules confuses clients.

## Breaking vs non-breaking

- **Non-breaking** (usually safe without a new major): add optional fields, add endpoints, relax validation, add enum values only if clients tolerate unknowns.
- **Breaking** (requires consumer coordination / major or explicit migration): remove fields, rename fields, change types, add required fields, narrow enums, change path or method.

The GitHub Action **OpenAPI contract** compares PRs to the merge base using **`oasdiff breaking`** so accidental breaking changes fail CI unless the contract is updated deliberately.

## Process

1. Change JAX-RS resources / OpenAPI annotations as needed.
2. Regenerate prod artifacts: **`make openapi-generate-prod`** (or **`make openapi-check-prod`** for generate + structural validation).
3. Optional: **`make openapi-spectral`** (Spectral rules in `.spectral.yaml`; CI runs this on `openapi/openapi.yaml`).
4. Commit **`openapi/openapi.yaml`** (and **`openapi/openapi.json`** if tracked) together with code changes.
5. CI must stay green: committed files must match `./mvnw -DskipTests package -Dquarkus.profile=prod` output (**`make openapi-check-sync`** reproduces the sync step locally).

## Related

- Workflow: `.github/workflows/openapi-contract.yml`
- Spectral config: `.spectral.yaml`
- ADR 0008 — Phase A (contract CI, Spectral) and API versioning backlog
