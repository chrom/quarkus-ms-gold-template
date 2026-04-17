# Перевірка Grafana та стеку (фази A–C)

Короткий чеклист після [grafana-configuration-plan.md](grafana-configuration-plan.md): **дані видно**, **лейбли узгоджені**, **кореляція працює**.

**Передумови:** `make up-metrics` (Prometheus, Grafana, Loki, Alloy, Jaeger); застосунок на `:8080` — `make up-prod` або `./mvnw quarkus:dev` (для метрик у Prometheus).

---

## A — Дані видно

| Крок | Дія | Очікування |
|------|-----|------------|
| A1 | Відкрити `http://localhost:9090/targets` | Target `quarkus-ms-gold-template` у стані **UP** (якщо застосунок запущений на хості `:8080`) |
| A2 | `curl -s http://localhost:8080/q/metrics \| head` | Текст метрик Micrometer |
| A3 | Grafana `http://localhost:3000` (admin / admin) → **Connections → Data sources** → Prometheus / Loki / Jaeger → **Save & test** | Успішно |
| A4 | **Dashboards** → `Quarkus Cloud Template — Overview` | Панелі з метриками не «No data» (при працюючому scrape) |

Швидка перевірка з терміналу: `make verify-observability`.

---

## B — Узгоджені лейбли (`job`)

| Джерело | Значення |
|---------|----------|
| `deploy/monitoring/prometheus.yml` | `job_name: 'quarkus-ms-gold-template'` |
| Дашборд (PromQL) | `job="quarkus-ms-gold-template"` на всіх панелях метрик |
| Змінна `instance` | `label_values(up{job="quarkus-ms-gold-template"}, instance)` |
| Jaeger → Metrics (`grafana/provisioning/datasources/jaeger.yaml`) | `job="quarkus-ms-gold-template"` у запитах `tracesToMetrics` |

Якщо зміните ім’я job у Prometheus — оновіть **дашборд**, **jaeger.yaml** і цю таблицю.

---

## C — Кореляція (логи ↔ трейси ↔ метрики)

| Сценарій | Як перевірити |
|----------|----------------|
| **Loki → Jaeger** | Explore → Loki → лог з полем `traceId` (JSON) → посилання **View trace** (derived field у datasource Loki) |
| **Jaeger → Loki** | Відкрити трейс → **Logs** (tracesToLogsV2 до Loki) |
| **Jaeger → Prometheus** | У трейсі → блок **Related metrics** / tracesToMetrics (RPS, bytes) |

**Примітка:** логи з `make up-prod` потрапляють у Loki через Alloy (Docker). `quarkus:dev` на хості в цей пайплайн за замовчуванням **не** потрапляє — див. [logging-loki-alloy.md](logging-loki-alloy.md).

---

## Типові проблеми

| Симптом | Що зробити |
|---------|------------|
| Prometheus target **DOWN** | Перевірити, що бекенд слухає `8080`; на Linux потрібен `host.docker.internal` (у compose вже `extra_hosts`). |
| Панелі порожні | Перевірити часовий діапазон у Grafana; згенерувати трафік (`curl` / k6). |
| Немає логів у Loki | Переконатися, що бекенд у **контейнері** (`quarkus_prod_backend`), Alloy має доступ до `docker.sock`. |

---

[← Observability](README.md) · [План Grafana](grafana-configuration-plan.md)
