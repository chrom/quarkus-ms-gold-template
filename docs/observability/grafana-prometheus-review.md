# Рев’ю: Grafana ↔ Prometheus (узгодження з документацією)

**Версії в репозиторії:** Grafana **11.6.14**, Prometheus **3.11.0**, Loki **3.7.1**, Jaeger all-in-one **1.76** (`deploy/docker-compose-metrics.yml`).

**Документація (закріплена під major):** [Grafana v11.6 — Prometheus](https://grafana.com/docs/grafana/v11.6/datasources/prometheus/configure/), [Jaeger](https://grafana.com/docs/grafana/v11.6/datasources/jaeger/), [Loki](https://grafana.com/docs/grafana/v11.6/datasources/loki/configure-loki-data-source/).

---

## Що перевірено і виправлено

| Тема | Було / ризик | Зроблено |
|------|----------------|----------|
| **Prometheus datasource** | Порядок полів `exemplarTraceIdDestinations`, неявні дефолти | Вирівняно під [офіційний приклад provisioning](https://grafana.com/docs/grafana/v11.6/datasources/prometheus/configure/#provision-the-prometheus-data-source): `datasourceUid` перед `name`; додано `manageAlerts` та `allowAsRecordingRulesTarget` як у документації. |
| **Ім’я лейбла exemplar** | `trace_id` vs `traceID` залежить від експорту | Поле **`name: trace_id`** — типовий ключ для OpenTelemetry / Prometheus exemplars; якщо посилання з метрик не відкривають трейс, перевірте фактичний лейбл: `curl -s http://localhost:8080/q/metrics \| rg exemplar` і за потреби змініть на `traceID`. |
| **timeInterval** | Має відповідати scrape | Залишено **15s** — як `global.scrape_interval` у `deploy/monitoring/prometheus.yml`. |
| **prometheusVersion** | Має відповідати серверу | **3.11.0** — збігається з образом `prom/prometheus:v3.11.0`. |
| **Jaeger tracesToMetrics** | Жорстко прошитий `job="..."` у запитах | Замінено на **`$__tags`** з мапінгом `service.name` → **`job`**, як у [прикладі Jaeger](https://grafana.com/docs/grafana/v11.6/datasources/jaeger/#provision-the-data-source). У YAML використано **`$$__tags`** (екранування Grafana для provisioning). Значення `service.name` (OTel) збігається з `job` у scrape для цього шаблону. |
| **Loki derived field `url`** | Лапки та `$${__value.raw}` | Залишено **internal link** до Jaeger; URL у лапках **`'$${__value.raw}'`** — відповідає макросу `${__value.raw}` у [Derived fields](https://grafana.com/docs/grafana/v11.6/datasources/loki/configure-loki-data-source/#derived-fields). |
| **Feature toggle** | traceToMetrics | У `deploy/docker-compose-metrics.yml` залишено `GF_FEATURE_TOGGLES_ENABLE=traceToMetrics` — потрібно для Trace to metrics у Grafana 11.x. |

---

## Залишкові нюанси (не помилки конфігу)

1. **Exemplars** у відповіді `/q/metrics` з’являться лише після увімкнення експорту exemplars у застосунку; до цього блок `exemplarTraceIdDestinations` у Prometheus datasource «мовчить» — це очікувано.
2. **tracesToMetrics** з `$__tags` вимагає наявності **`service.name`** у спанах (OpenTelemetry задає його з `quarkus.otel.service.name`).
3. **Локальний `quarkus:dev`** не потрапляє в Loki через Alloy+Docker — обмеження пайплайну, не Grafana (див. [logging-loki-alloy.md](logging-loki-alloy.md)).

---

## Швидка перевірка після змін

1. `make up-metrics` (і застосунок на `:8080`).
2. `make verify-observability`.
3. Grafana → Connections → Prometheus / Jaeger / Loki → **Save & test**.
4. Jaeger Explore → відкрити трейс → переконатися, що **Related metrics** виконують PromQL без помилки.

---

[← Observability](README.md) · [grafana-verify.md](grafana-verify.md)
