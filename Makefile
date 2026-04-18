# =============================================================
# Makefile for quarkus-ms-gold-template
# Usage: make <command>
# =============================================================
# Compose files live under deploy/ (paths inside YAML are relative to each file).
COMPOSE_PROD  := -f deploy/docker-compose.yml
COMPOSE_METRICS := docker compose -p metrics -f deploy/docker-compose-metrics.yml

.PHONY: help dev dev-secured test verify coverage it-pg check-docker sbom sbom-show sbom-list sbom-diff image-scan sonar-local ci-local build-native package-native \
	k3d-registry-check docker-build-native-image docker-tag-compose-for-k3d docker-push docker-build-push-native \
	up-prod stop-prod stop-all clean-prod clean-metrics clean-all docker-clean-project prune-all \
	up-metrics down-prod down-metrics down-all logs-app logs-db status verify-observability grafana-reset-admin \
	openapi-generate-prod openapi-generate-dev openapi-generate openapi-validate-prod openapi-validate-dev openapi-validate \
	openapi-check-prod openapi-check-dev openapi-check openapi-check-sync openapi-spectral openapi-diff \
	helm-install helm-uninstall helm-diff helm-sync-dashboard

# OpenAPI: export dir (must match application.properties %dev / %prod)
OPENAPI_DIR := openapi
# Docker image for structural validation (OpenAPI 3.x)
OPENAPI_VALIDATOR := docker run --rm -v "$(CURDIR):/local" openapitools/openapi-generator-cli:v7.10.0 validate -i
# Spectral (Stoplight) — must match .github/workflows/openapi-contract.yml
SPECTRAL_IMG := stoplight/spectral@sha256:b3d5a530f83c4a72df69e682c5ac928bc9821b5ca3c42529e81d926c80fa50ab
SPECTRAL := docker run --rm -v "$(CURDIR):/work" -w /work $(SPECTRAL_IMG) lint openapi/openapi.yaml --fail-severity error

# Default — show help
.DEFAULT_GOAL := help

help: ## 📋 Show all available commands
	@echo ""
	@echo "  ╔══════════════════════════════════════════════════╗"
	@echo "  ║       Quarkus MS Gold — Available commands       ║"
	@echo "  ╚══════════════════════════════════════════════════╝"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""

# ── DEVELOPMENT ───────────────────────────────────────────────
# OpenAPI: before dev / prod deploy, spec is regenerated and validated (Docker). Skip: SKIP_OPENAPI_VALIDATE=1 make dev

dev: ## 🚀 Run Quarkus in dev mode (OpenAPI check first unless SKIP_OPENAPI_VALIDATE=1)
	@if [ "$(SKIP_OPENAPI_VALIDATE)" != "1" ]; then $(MAKE) openapi-check-dev; else echo "  ⚠️  OpenAPI check skipped (SKIP_OPENAPI_VALIDATE=1)"; fi
	./mvnw quarkus:dev

dev-secured: ## 🔐 Dev mode + OIDC profile (needs issuer; see docs/security/oidc-secured-profile.md)
	@if [ "$(SKIP_OPENAPI_VALIDATE)" != "1" ]; then $(MAKE) openapi-check-dev; else echo "  ⚠️  OpenAPI check skipped (SKIP_OPENAPI_VALIDATE=1)"; fi
	./mvnw quarkus:dev -Dquarkus.profile=dev,secured

# Docker daemon is a hard prerequisite for `test`, `it-pg`, and `verify` — both
# @QuarkusTest classes (Dev Services) and FlywayMigrationIT (raw Testcontainers) need
# a reachable engine. Fail fast here instead of leaking confusing Testcontainers stack
# traces into Surefire/Failsafe output. See ADR 0011.
check-docker:
	@docker info >/dev/null 2>&1 || { \
		echo "  ❌ Docker daemon is not reachable."; \
		echo "     quarkus-ms-gold-template's test suite runs against PostgreSQL via"; \
		echo "     Testcontainers (see ADR 0011). Start Docker and retry."; \
		exit 1; \
	}

test: check-docker ## 🧪 Run unit + @QuarkusTest classes (PostgreSQL via Dev Services)
	./mvnw test

it-pg: check-docker ## 🐘 Run only FlywayMigrationIT against a fresh postgres:16-alpine
	# -Dsurefire.failIfNoSpecifiedTests=false: Surefire's `test` phase still runs even
	# when we only care about Failsafe IT, so tell it "no match is OK". -Dtest='!*'
	# keeps Surefire silent; Failsafe picks up IT via -Dit.test.
	./mvnw -B -ntp verify -DskipITs=false -Dit.test=FlywayMigrationIT -Dtest='!*' -Dsurefire.failIfNoSpecifiedTests=false

# ── OPENAPI (SmallRye — build-time export + validate) ─────────
# Requires Docker for openapi-validate-* (openapitools/openapi-generator-cli).

openapi-generate-prod: ## 📄 Generate openapi/openapi.yaml (+ .json) — quarkus.profile=prod
	@mkdir -p $(OPENAPI_DIR)
	./mvnw -DskipTests package -Dquarkus.profile=prod
	@test -f $(OPENAPI_DIR)/openapi.yaml || { echo "  ❌ missing $(OPENAPI_DIR)/openapi.yaml"; exit 1; }
	@echo "  ✅ $(OPENAPI_DIR)/openapi.yaml (prod)"

openapi-generate-dev: ## 📄 Generate openapi/openapi-dev.yaml (+ .json) — quarkus.profile=dev
	@mkdir -p $(OPENAPI_DIR)
	./mvnw -DskipTests package -Dquarkus.profile=dev
	@test -f $(OPENAPI_DIR)/openapi-dev.yaml || { echo "  ❌ missing $(OPENAPI_DIR)/openapi-dev.yaml"; exit 1; }
	@echo "  ✅ $(OPENAPI_DIR)/openapi-dev.yaml (dev)"

openapi-generate: openapi-generate-prod openapi-generate-dev ## 📄 Generate prod + dev OpenAPI specs

openapi-validate-prod: ## ✔️  Validate openapi/openapi.yaml (requires Docker; run openapi-generate-prod if file missing)
	@test -f $(OPENAPI_DIR)/openapi.yaml || { echo "  ❌ Run: make openapi-generate-prod"; exit 1; }
	@echo "  ▶ Validating $(OPENAPI_DIR)/openapi.yaml …"
	@$(OPENAPI_VALIDATOR) /local/$(OPENAPI_DIR)/openapi.yaml
	@echo "  ✅ OpenAPI prod spec OK"

openapi-validate-dev: ## ✔️  Validate openapi/openapi-dev.yaml (requires Docker)
	@test -f $(OPENAPI_DIR)/openapi-dev.yaml || { echo "  ❌ Run: make openapi-generate-dev"; exit 1; }
	@echo "  ▶ Validating $(OPENAPI_DIR)/openapi-dev.yaml …"
	@$(OPENAPI_VALIDATOR) /local/$(OPENAPI_DIR)/openapi-dev.yaml
	@echo "  ✅ OpenAPI dev spec OK"

openapi-validate: openapi-validate-prod openapi-validate-dev ## ✔️  Validate both YAML files (requires Docker)

openapi-check-prod: openapi-generate-prod openapi-validate-prod ## 📄+✔️  Regenerate prod spec and validate (deploy / CI)
openapi-check-dev: openapi-generate-dev openapi-validate-dev ## 📄+✔️  Regenerate dev spec and validate (local dev)

openapi-check: openapi-generate openapi-validate ## 📄+✔️  Full: generate prod + dev, validate both

openapi-spectral: ## 🔍 Spectral lint on prod OpenAPI (run after: make openapi-generate-prod)
	@test -f $(OPENAPI_DIR)/openapi.yaml || { echo "  ❌ No $(OPENAPI_DIR)/openapi.yaml — run: make openapi-generate-prod"; exit 1; }
	@echo "  ▶ Spectral ($(OPENAPI_DIR)/openapi.yaml) …"
	@$(SPECTRAL)
	@echo "  ✅ Spectral OK (errors block CI; warnings are reported only)"

openapi-check-sync: ## 📄 Same as CI: committed openapi.yaml must match prod codegen (no drift)
	@test -f $(OPENAPI_DIR)/openapi.yaml || { echo "  ❌ Run: make openapi-check-prod"; exit 1; }
	@cp $(OPENAPI_DIR)/openapi.yaml $(OPENAPI_DIR)/.openapi-committed.tmp.yaml
	@./mvnw -q -DskipTests package -Dquarkus.profile=prod
	@cmp -s $(OPENAPI_DIR)/.openapi-committed.tmp.yaml $(OPENAPI_DIR)/openapi.yaml || { \
		rm -f $(OPENAPI_DIR)/.openapi-committed.tmp.yaml; \
		echo "  ❌ $(OPENAPI_DIR)/openapi.yaml out of sync with codegen. Run: make openapi-check-prod && git add $(OPENAPI_DIR)/"; \
		exit 1; \
	}
	@rm -f $(OPENAPI_DIR)/.openapi-committed.tmp.yaml
	@echo "  ✅ committed OpenAPI matches prod codegen (CI sync check)"

openapi-diff: openapi-generate ## 📊 Diff dev vs prod specs (Docker: oasdiff; URLs/servers may differ)
	@echo "  ▶ oasdiff: openapi-dev.yaml vs openapi.yaml …"
	@docker run --rm -v "$(CURDIR):/w" tufin/oasdiff:latest diff /w/$(OPENAPI_DIR)/openapi-dev.yaml /w/$(OPENAPI_DIR)/openapi.yaml || true
	@echo "  (exit 0 from make even if diff is non-empty; inspect output above)"

# ── BUILD ─────────────────────────────────────────────────────
# HELM_* must be defined before IMAGE_NAME (:= expands immediately).
# k3d: `k3d registry create registry.localhost --port 5000` → container host k3d-registry.localhost:5000
# (cluster pulls). From the workstation, curl/docker often work only via localhost:5000 — same registry
# (see https://k3d.io/usage/registries/ "Preface: Referencing local registries").
K3D_REGISTRY_PORT ?= 5000
K3D_REGISTRY ?= k3d-registry.localhost:$(K3D_REGISTRY_PORT)
K3D_REGISTRY_PUSH ?= localhost:$(K3D_REGISTRY_PORT)
HELM_RELEASE := quarkus-ms-gold-template
HELM_IMAGE_TAG ?= latest
IMAGE_NAME := $(K3D_REGISTRY_PUSH)/$(HELM_RELEASE):$(HELM_IMAGE_TAG)
# deploy/docker-compose.yml → Compose project name "deploy" → image deploy-backend-service:latest (not $(IMAGE_NAME))
COMPOSE_PROD_PROJECT ?= deploy
COMPOSE_BACKEND_IMAGE := $(COMPOSE_PROD_PROJECT)-backend-service

k3d-registry-check: ## 🔎 Verify local k3d registry is reachable (cluster name and/or localhost)
	@echo "  ▶ Checking k3d registry (port $(K3D_REGISTRY_PORT)) …"
	@ok=0; \
	for base in "$(K3D_REGISTRY)" "$(K3D_REGISTRY_PUSH)"; do \
	  u="http://$$base/v2/"; \
	  if curl -sf "$$u" >/dev/null 2>&1; then \
	    echo "  ✅ Registry API reachable at $$u"; \
	    ok=1; \
	    break; \
	  fi; \
	done; \
	if [ "$$ok" != 1 ]; then \
	  echo "  ❌ Registry not reachable on port $(K3D_REGISTRY_PORT) (tried $(K3D_REGISTRY) and $(K3D_REGISTRY_PUSH))."; \
	  echo "     Create it from infra-bootstrap:  make up"; \
	  echo "     Or manually:  k3d registry create registry.localhost --port $(K3D_REGISTRY_PORT)"; \
	  echo "     If the registry runs but only localhost works, docker push uses $(K3D_REGISTRY_PUSH); Helm still uses $(K3D_REGISTRY) for pulls."; \
	  echo "     Alternative:  k3d image import <tar> -c gold-dev"; \
	  exit 1; \
	fi

docker-build-native-image: package-native ## 🐳 Build native image (tagged for k3d registry)
	@if ! ls target/*-runner >/dev/null 2>&1; then \
		echo "  ❌ No native executable target/*-runner after package-native."; \
		echo "     Run: make package-native   (or check Maven native build errors)"; \
		ls -la target 2>/dev/null || true; \
		exit 1; \
	fi
	@echo "  ▶ Building Docker image (native): $(IMAGE_NAME)"
	docker build -f src/main/docker/Dockerfile.native-micro -t $(IMAGE_NAME) .

# After `make up-prod` / compose build, the image is tagged $(COMPOSE_BACKEND_IMAGE):latest, not $(IMAGE_NAME).
docker-tag-compose-for-k3d: ## 📎 Retag compose-built image ($(COMPOSE_BACKEND_IMAGE)) as $(IMAGE_NAME) for k3d push
	@docker image inspect $(COMPOSE_BACKEND_IMAGE):latest >/dev/null 2>&1 || { \
		echo "  ❌ No local image $(COMPOSE_BACKEND_IMAGE):latest."; \
		echo "     Build with:  docker compose $(COMPOSE_PROD) build backend-service"; \
		echo "     Or:         make up-prod   (builds backend image)"; \
		exit 1; \
	}
	docker tag $(COMPOSE_BACKEND_IMAGE):latest $(IMAGE_NAME)
	@echo "  ✅ Tagged $(COMPOSE_BACKEND_IMAGE):latest → $(IMAGE_NAME)"

docker-push: k3d-registry-check ## 📤 Push to k3d registry (needs $(IMAGE_NAME); see docker-tag-compose-for-k3d if you used up-prod)
	@docker image inspect $(IMAGE_NAME) >/dev/null 2>&1 || { \
		echo "  ❌ No local image $(IMAGE_NAME)."; \
		echo "     • Full native build + tag:  make docker-build-native-image"; \
		echo "     • Or one step:               make docker-build-push-native"; \
		echo "     • If you already built via up-prod/compose ($(COMPOSE_BACKEND_IMAGE)):  make docker-tag-compose-for-k3d"; \
		exit 1; \
	}
	@echo "  ▶ Pushing: $(IMAGE_NAME)"
	docker push $(IMAGE_NAME)
	@echo "  ✅ Pushed. Deploy with: make helm-install HELM_IMAGE_TAG=$(HELM_IMAGE_TAG)"

docker-build-push-native: docker-build-native-image docker-push ## 🐳 Build native image and push to k3d registry

build-native: ## 🔨 Interactive Native build (profile from prompt; see scripts/build-native.sh)
	./scripts/build-native.sh

# Non-interactive build for metrics stack (same Dockerfile.native-micro as in deploy/docker-compose.yml).
package-native: ## 📦 Native + GraalVM container compilation (for backend image in deploy/docker-compose.yml)
	./mvnw package -Dnative -Dquarkus.native.container-build=true -Dquarkus.profile=prod -DskipTests \
		-Dquarkus.native.native-image-xmx=20g

# ── PRODUCTION ENVIRONMENT ────────────────────────────────────

up-prod: ## ▶️  Run: PostgreSQL + Backend (OpenAPI check first unless SKIP_OPENAPI_VALIDATE=1)
	@if [ "$(SKIP_OPENAPI_VALIDATE)" != "1" ]; then $(MAKE) openapi-check-prod; else echo "  ⚠️  OpenAPI check skipped (SKIP_OPENAPI_VALIDATE=1)"; fi
	docker compose $(COMPOSE_PROD) up -d --build
	@echo ""
	@echo "  ✅ Prod stack started!"
	@echo "  🌐 Backend: http://localhost:8080"
	@echo "  📖 Swagger: http://localhost:8080/q/swagger-ui"
	@echo "  ⏹️  Stop: make stop-prod  (or make down-prod)"
	@echo ""

down-prod: ## ⏹️  Stop prod stack (docker compose down)
	docker compose $(COMPOSE_PROD) down

stop-prod: down-prod ## ⏹️  Stop what was started via up-prod (calls down-prod)
	@echo ""
	@echo "  ✅ Prod stack stopped."
	@echo ""

# ── MONITORING ────────────────────────────────────────────────

up-metrics: ## 📊 Monitoring only: Prometheus + Grafana + Loki + Alloy + Jaeger (no app)
	$(COMPOSE_METRICS) up -d --build
	@echo ""
	@echo "  ✅ Monitoring stack started (without Quarkus)."
	@echo "  ▶️  Run app separately: make up-prod  or  ./mvnw quarkus:dev  → http://localhost:8080"
	@echo "  🔍 Prometheus:  http://localhost:9090   (scrapes host.docker.internal:8080)"
	@echo "  📈 Grafana:      http://localhost:3000  (admin / admin) → Dashboards → Quarkus Demo → «Quarkus Demo — Overview»; Explore: Loki / Jaeger"
	@echo "  🔎 Jaeger UI:    http://localhost:16686   OTLP gRPC: localhost:4317"
	@echo "  📡 Logs in Loki: from Docker containers only (Alloy + docker.sock). quarkus:dev on host — not in Loki."
	@echo ""

down-metrics: ## ⏹️  Stop monitoring stack
	$(COMPOSE_METRICS) down

grafana-reset-admin: ## 🔑 Reset Grafana admin password to admin (needs: metrics-grafana running)
	@docker exec metrics-grafana grafana cli admin reset-admin-password admin
	@echo "  ✅ Grafana admin password set to: admin  → http://localhost:3000"

verify-observability: ## 🩺 Health checks: Prometheus, Grafana, Loki, Jaeger (needs: make up-metrics)
	@echo ""
	@echo "  ── Monitoring stack (localhost) ──"
	@curl -sf http://localhost:9090/-/healthy >/dev/null && echo "  ✅ Prometheus  :9090" || { echo "  ❌ Prometheus — run: make up-metrics"; exit 1; }
	@curl -sf http://localhost:3000/api/health | grep -q database && echo "  ✅ Grafana     :3000" || { echo "  ❌ Grafana"; exit 1; }
	@curl -sf http://localhost:3100/ready >/dev/null && echo "  ✅ Loki        :3100" || echo "  ⚠️  Loki        :3100 (not ready)"
	@curl -sf -o /dev/null http://localhost:16686 && echo "  ✅ Jaeger UI   :16686" || echo "  ⚠️  Jaeger      :16686"
	@echo "  ── Scrape (app must listen on :8080) ──"
	@curl -sf "http://localhost:9090/api/v1/query?query=up%7Bjob%3D%22quarkus-ms-gold-template%22%7D" | grep -q '"status":"success"' && echo "  ✅ PromQL job=quarkus-ms-gold-template query OK" || echo "  ❌ Prometheus query failed"
	@echo ""
	@echo "  📖 Full checklist: docs/observability/grafana-verify.md"
	@echo ""

# ── STOP EVERYTHING ───────────────────────────────────────────

down-all: ## 🛑 Stop ALL stacks (containers; volumes remain)
	docker compose $(COMPOSE_PROD) down
	$(COMPOSE_METRICS) down

stop-all: down-all ## ⏹️  Same as down-all — just stop, data in Docker volumes is not deleted
	@echo ""
	@echo "  ✅ All containers stopped. DB/metrics data in volumes preserved."
	@echo "  🗑️  Full cleanup (including volumes): make clean-all"
	@echo ""

# ── DELETE EVERYTHING (volumes) ────────────────────────────────

clean-prod: ## 🗑️  Stop prod stack and delete its volumes (DB fresh; needed after PG17→18 or broken pg_data)
	docker compose $(COMPOSE_PROD) down -v --remove-orphans
	@echo ""
	@echo "  ✅ Prod stack stopped, volumes (pg_data etc.) deleted."
	@echo ""

clean-metrics: ## 🗑️  Stop metrics stack and delete volumes (Prometheus/Grafana data will be lost)
	$(COMPOSE_METRICS) down -v
	@echo ""
	@echo "  ✅ Monitoring stack stopped, volumes deleted."
	@echo ""

clean-all: docker-clean-project ## 🗑️  Full cleanup: app + monitoring (containers, networks, volumes, local images)
	@echo ""
	@echo "  ✅ clean-all: removed ALL Docker resources for this repo (prod + metrics)."
	@echo ""

# Full removal of everything Compose created for this repo (both projects) + fixed container_name leftovers.
docker-clean-project: ## 🗑 Remove ALL Docker resources for this project only (prod + metrics: containers, networks, volumes, built images)
	docker compose $(COMPOSE_PROD) down -v --rmi local --remove-orphans
	$(COMPOSE_METRICS) down -v --rmi local --remove-orphans
	@echo ""
	@echo "  Removing fixed-name containers if they still exist..."
	-@docker rm -f quarkus_prod_db quarkus_prod_backend 2>/dev/null || true
	-@docker rm -f metrics-jaeger metrics-loki metrics-prometheus metrics-grafana metrics-alloy 2>/dev/null || true
	@echo ""
	@echo "  ✅ docker-clean-project: project Docker resources removed."
	@echo ""

prune-all: docker-clean-project ## 🗑 [alias] same as docker-clean-project

# ── LOGS ──────────────────────────────────────────────────────

logs-app: ## 📜 View backend logs (live)
	docker compose $(COMPOSE_PROD) logs -f backend-service

logs-db: ## 📜 View database logs (live)
	docker compose $(COMPOSE_PROD) logs -f pg-database

# ── STATUS ────────────────────────────────────────────────────

status: ## 🩺 Show status of all containers
	@echo "\n── Prod stack ─────────────────────────────────"
	@docker compose $(COMPOSE_PROD) ps 2>/dev/null || echo "  (not running)"
	@echo "\n── Monitoring stack (metrics project) ─────────"
	@$(COMPOSE_METRICS) ps 2>/dev/null || echo "  (not running)"
	@echo ""

# ── LOAD TESTING (k6) ──────────────────────────────────────────
# Usage: make load-test-docker DURATION=10m VUS=50 BASE_URL=http://my-service.localhost

VUS ?= 10
DURATION ?= 1m
BASE_URL ?= http://my-service.localhost

install-k6: ## ⬇️  Install k6 (Linux/Ubuntu/Debian)
	@echo "  📦 Installing k6..."
	sudo gpg -k
	sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
	echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
	sudo apt-get update
	sudo apt-get install k6
	@echo "  ✅ k6 installed!"
	@echo "  ✅ USE LIKE: make load-test-docker DURATION=10m VUS=50"

load-test: ## 🚀 Run load test (locally; requires k6)
	@echo "  🔥 Launching k6: $(VUS) users, $(DURATION) duration..."
	VUS=$(VUS) DURATION=$(DURATION) BASE_URL=$(BASE_URL) k6 run load-tests/k6/k6-load-test.js

load-test-docker: ## 🐳 Run load test via Docker (no k6 installation required)
	@echo "  🔥 Launching k6 (Docker): $(VUS) users, $(DURATION) duration → $(BASE_URL)"
	docker run --rm -i --network=host -e VUS=$(VUS) -e DURATION=$(DURATION) -e BASE_URL=$(BASE_URL) grafana/k6 run - <load-tests/k6/k6-load-test.js

# ── CI / SUPPLY CHAIN (local mirror) ──────────────────────────
# Local equivalents of .github/workflows/ci.yaml steps.
# Same commands, same flags — so "green locally" ≈ "green on PR".
# ADR: docs/adr/0009-ci-supply-chain-baseline.md

# Must match ci.yaml env.IMAGE_NAME for parity; tag uses short git sha locally.
CI_IMAGE_NAME := quarkus-ms-gold-template
CI_IMAGE_TAG  ?= ci-$(shell git rev-parse --short HEAD 2>/dev/null || echo local)
CI_IMAGE_REF  := $(CI_IMAGE_NAME):$(CI_IMAGE_TAG)
# Pin Trivy to the same major version the workflow uses (aquasecurity/trivy-action@0.28.0).
TRIVY_IMG     := aquasec/trivy:0.58.1

verify: check-docker ## ✅ CI-parity: mvn clean verify -DskipITs=false (tests + JaCoCo + SBOM)
	@echo "  ▶ Full verify (unit + IT + coverage + SBOM) …"
	./mvnw -B -ntp clean verify -DskipITs=false
	@echo "  ✅ JaCoCo: target/site/jacoco/index.html"
	@echo "  ✅ SBOM:   target/bom.json, target/bom.xml"

coverage: verify ## 📊 Run verify + show JaCoCo report path (open in browser)
	@echo ""
	@echo "  📊 JaCoCo HTML: file://$(CURDIR)/target/site/jacoco/index.html"
	@if command -v xdg-open >/dev/null 2>&1; then xdg-open target/site/jacoco/index.html >/dev/null 2>&1 || true; fi

sbom: ## 📦 Regenerate CycloneDX SBOM only (fast path, no tests)
	./mvnw -B -ntp -DskipTests package cyclonedx:makeAggregateBom
	@$(MAKE) --no-print-directory sbom-show

sbom-show: ## 🔎 Summarise target/bom.json (format, components count)
	@test -f target/bom.json || { echo "  ❌ No target/bom.json — run: make sbom"; exit 1; }
	@python3 -c "import json; b=json.load(open('target/bom.json')); \
		print(f\"  bomFormat:   {b['bomFormat']}\"); \
		print(f\"  specVersion: {b['specVersion']}\"); \
		print(f\"  components:  {len(b.get('components',[]))}\")"

sbom-list: ## 📜 Print every SBOM component as 'group:name@version' (sorted, pipe to grep/less)
	@test -f target/bom.json || { echo "  ❌ No target/bom.json — run: make sbom"; exit 1; }
	@python3 -c "import json; \
		comps=json.load(open('target/bom.json')).get('components',[]); \
		rows=sorted({(c.get('group') or '-', c.get('name') or '-', c.get('version') or '-') for c in comps}); \
		[print(f'{g}:{n}@{v}') for g,n,v in rows]"

# Baseline SBOM to diff against. Override with: make sbom-diff BASE=/path/to/old-bom.json
# Typical workflow: on main -> `make sbom && cp target/bom.json /tmp/bom.base.json`;
#                   on feature/Dependabot branch -> `make sbom && make sbom-diff`.
BASE ?= /tmp/bom.base.json

sbom-diff: ## 🔀 Diff current SBOM vs BASE (added / removed / upgraded) — set BASE=<path>
	@test -f target/bom.json || { echo "  ❌ No target/bom.json — run: make sbom"; exit 1; }
	@test -f "$(BASE)" || { \
		echo "  ❌ Baseline not found: $(BASE)"; \
		echo "     Generate one, e.g.: git switch main && make sbom && cp target/bom.json $(BASE)"; \
		exit 1; }
	@echo "  ▶ Comparing target/bom.json  vs  $(BASE)"
	@python3 -c "import json; \
		cur  = {(c.get('group') or '-', c.get('name') or '-'): (c.get('version') or '-') for c in json.load(open('target/bom.json')).get('components', [])}; \
		base = {(c.get('group') or '-', c.get('name') or '-'): (c.get('version') or '-') for c in json.load(open('$(BASE)')).get('components', [])}; \
		added    = sorted(k for k in cur if k not in base); \
		removed  = sorted(k for k in base if k not in cur); \
		upgraded = sorted([(k, base[k], cur[k]) for k in cur if k in base and base[k] != cur[k]]); \
		print(f'  + added:    {len(added)}'); \
		[print(f'      + {g}:{n}@{cur[(g,n)]}') for g,n in added]; \
		print(f'  - removed:  {len(removed)}'); \
		[print(f'      - {g}:{n}@{base[(g,n)]}') for g,n in removed]; \
		print(f'  ~ upgraded: {len(upgraded)}'); \
		[print(f'      ~ {t[0][0]}:{t[0][1]}  {t[1]} -> {t[2]}') for t in upgraded]"

image-scan: ## 🛡️  CI-parity: build Dockerfile.jvm locally and run Trivy (HIGH/CRITICAL, ignore-unfixed)
	@test -d target/quarkus-app || { echo "  ❌ target/quarkus-app missing — run: make verify"; exit 1; }
	@echo "  ▶ Building JVM image: $(CI_IMAGE_REF)"
	docker build -f src/main/docker/Dockerfile.jvm -t $(CI_IMAGE_REF) .
	@echo "  ▶ Trivy scan (same filters as ci.yaml fail-gate) …"
	docker run --rm \
		-v /var/run/docker.sock:/var/run/docker.sock \
		$(TRIVY_IMG) image \
			--severity HIGH,CRITICAL \
			--ignore-unfixed \
			--vuln-type os,library \
			--scanners vuln,secret,misconfig \
			--exit-code 1 \
			--format table \
			--timeout 5m \
			$(CI_IMAGE_REF)
	@echo "  ✅ image-scan clean: $(CI_IMAGE_REF)"

sonar-local: ## 📡 Run SonarQube scan against $$SONAR_HOST_URL (needs SONAR_TOKEN; runs verify first)
	@[ -n "$$SONAR_HOST_URL" ] || { echo "  ❌ SONAR_HOST_URL not set"; exit 1; }
	@[ -n "$$SONAR_TOKEN" ]    || { echo "  ❌ SONAR_TOKEN not set";    exit 1; }
	$(MAKE) verify
	./mvnw -B -ntp sonar:sonar \
		-Dsonar.host.url="$$SONAR_HOST_URL" \
		-Dsonar.token="$$SONAR_TOKEN" \
		-Dsonar.qualitygate.wait=true

ci-local: ## 🧪 Full CI mirror: openapi-check-sync + verify + image-scan (what PR will run)
	$(MAKE) openapi-check-sync
	$(MAKE) verify
	$(MAKE) image-scan
	@echo ""
	@echo "  ✅ ci-local: all PR gates passed on this workstation."

# ── HELM (K8s deployment — k3d or cloud) ─────────────────────
# Requires: k3d cluster (make k3d-bootstrap in infra-bootstrap)
# ADR: infra-bootstrap/docs/adr/0001-platform-infrastructure-decisions.md §3
# ADR: infra-bootstrap/docs/adr/0002-service-auto-registration.md
# HELM_RELEASE / HELM_IMAGE_TAG / IMAGE_NAME — see BUILD section above.
HELM_NAMESPACE := default
HELM_CHART := deploy/helm
HELM_VALUES := deploy/helm/values.yaml

helm-install: ## ☁️ Deploy to k3d via Helm (ServiceMonitor + Grafana dashboard auto-registered)
	@echo ""
	@echo "  ▶ Deploying $(HELM_RELEASE) to k3d cluster…"
	$(MAKE) helm-sync-dashboard
	helm upgrade --install $(HELM_RELEASE) $(HELM_CHART) \
		-f $(HELM_VALUES) \
		--set image.repository=$(K3D_REGISTRY)/$(HELM_RELEASE) \
		--set image.tag=$(HELM_IMAGE_TAG) \
		--namespace $(HELM_NAMESPACE) --create-namespace
	@echo ""
	@echo "  ✅ Deployed! Verifying pods..."
	@kubectl get deployment -n $(HELM_NAMESPACE) -l app.kubernetes.io/instance=$(HELM_RELEASE) -o name | \
		xargs -r -n1 kubectl rollout status -n $(HELM_NAMESPACE) --timeout=180s
	@echo ""
	@echo "  📊 Prometheus: check ServiceMonitor was picked up:"
	@echo "    kubectl get servicemonitor -n $(HELM_NAMESPACE)"
	@echo "  📊 Grafana: dashboard should appear in Grafana UI under 'Services' folder"
	@echo ""

helm-uninstall: ## 🗑️ Remove service from cluster (deregisters Prometheus + Grafana dashboard)
	helm uninstall $(HELM_RELEASE) --namespace $(HELM_NAMESPACE)
	@echo "  ✅ $(HELM_RELEASE) removed. ServiceMonitor and Dashboard ConfigMap deleted."

helm-diff: ## 🔍 Preview Helm changes (requires: helm plugin install https://github.com/databus23/helm-diff)
	helm diff upgrade $(HELM_RELEASE) $(HELM_CHART) \
		-f $(HELM_VALUES) \
		--set image.tag=$(HELM_IMAGE_TAG) \
		--namespace $(HELM_NAMESPACE)

helm-sync-dashboard: ## 📁 Sync K8s Grafana dashboard JSON → deploy/helm/files/dashboards/
	@echo "  ℹ️  K8s Grafana dashboard is maintained under deploy/helm/files/dashboards/."
	@echo "     (Local Docker Grafana uses grafana/dashboards/; K8s uses ConfigMap sidecar.)"
	@echo "  ✅ No-op. Run 'make helm-install' after changing deploy/helm/files/dashboards/quarkus-overview.json."
