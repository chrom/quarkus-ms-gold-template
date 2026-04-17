# Observability (додаткові нотатки)

Матеріали поза окремими розділами курсу, що стосуються **логів, метрик і трейсів** у повному стеку.

| Документ | Зміст |
|----------|--------|
| [grafana-configuration-plan.md](grafana-configuration-plan.md) | План Grafana: рішення, фази A–F, критерії готовності |
| [grafana-verify.md](grafana-verify.md) | Перевірка стеку (фази A–C): метрики, `job`, кореляція лог↔трейс |
| [grafana-prometheus-review.md](grafana-prometheus-review.md) | Рев’ю узгодження Grafana/Prometheus з документацією v11.6 |
| [monitoring-optimization-plan.md](monitoring-optimization-plan.md) | План моніторингу (Micrometer, Grafana, Jaeger) і статус впровадження |
| [metrics-inventory.md](metrics-inventory.md) | Шари метрик (SLI / Vert.x / Netty) і як перевірити `/q/metrics` |
| [logging-loki-alloy.md](logging-loki-alloy.md) | Loki + **Grafana Alloy** (замість EOL Promtail), ланцюжок від Quarkus до Grafana |
| [health-checks.md](health-checks.md) | **Health** (liveness / readiness / startup): ролі, що вже в коді, план наступних кроків |
| [database-migrations.md](database-migrations.md) | **Flyway + Hibernate (`validate`)**, expand/contract, порядок старту «під капотом» |

- **`deploy/docker-compose-metrics.yml`** — лише Prometheus / Grafana / Loki / Alloy / Jaeger.  
- **`deploy/docker-compose.yml`** — застосунок + PostgreSQL (`make up-prod`).

### Grafana (після `make up-metrics`)

- Вхід: [http://localhost:3000](http://localhost:3000) — `admin` / `admin`.
- **Джерела даних** (автопідключення з `grafana/provisioning/datasources/`): Prometheus (за замовчуванням), Loki, Jaeger.
- **Дашборд** «Quarkus Cloud Template — Overview»: меню **Dashboards** або `/d/quarkus-ms-gold-template-overview`. Панелі Micrometer з фільтром `job="quarkus-ms-gold-template"`; логи Docker → Loki (`{job="docker"}`). Метрики з’являться, коли застосунок слухає `:8080` і Prometheus його скрапить (`host.docker.internal:8080`).
- Швидка перевірка: `make verify-observability` · детальний чеклист — [grafana-verify.md](grafana-verify.md).
- Зміни в JSON-дашборді: `grafana/dashboards/quarkus-overview.json` (перезапуск Grafana або **Save** у UI з `allowUiUpdates: true` у провайдері).

**Конфлікт імені контейнера (`metrics-jaeger` already in use):** старі `metrics-*` залишилися від попереднього запуску з іншим compose-проєктом. Зупини й видали їх:  
`docker stop metrics-jaeger metrics-loki metrics-prometheus metrics-grafana metrics-alloy && docker rm metrics-jaeger metrics-loki metrics-prometheus metrics-grafana metrics-alloy`  
потім знову `make up-metrics`.

[← Корінь `docs/`](../)
