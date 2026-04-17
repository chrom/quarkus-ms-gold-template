# Централізовані логи: Loki та Grafana Alloy

## Навіщо цей документ

Для **агрегації логів** у стеку Grafana зазвичай використовують **Loki** (сховище) + **колектор**, який зчитує логи контейнерів або файлів і **відправляє** їх у Loki. Раніше типовим колектором був **Promtail**; з **2 березня 2026** Promtail оголошено **EOL** (без подальших оновлень і комерційної підтримки). Нові рішення та розвиток функцій — у **Grafana Alloy**.

> **Виняток:** клієнт **lambda-promtail** до цього EOL не відносять.

Офіційна документація Alloy: [Grafana Alloy](https://grafana.com/docs/alloy/latest/).

## Роль кожного компонента

| Компонент | Задача |
|-----------|--------|
| **Quarkus** | Писати логи в **stdout** (у production бажано **JSON** через `quarkus-logging-json`) з полями на кшталт `traceId`, `requestId`. |
| **Docker / Kubernetes** | Зберігати stdout контейнера в **json-file** (або інший драйвер) або надавати API для збору. |
| **Grafana Alloy** | Виявляти джерела (Docker, Kubernetes, файли), **парсити** рядки за потреби, **пушити** в Loki API. |
| **Loki** | Зберігати та індексувати логи (лейбли + стиснення), відповідати на запити **LogQL**. |
| **Grafana** | Джерело даних Loki, **Explore**, дашборди, алерти по логах. |

## Чому не Promtail у нових проєктах

- **EOL** — відсутність патчів і еволюції під нові ОС / Docker / K8s.
- **Alloy** — рекомендований наступник: один агент може поєднувати **логи, метрики, трейси** (за потреби) з конфігом **River**.

## Як «передаємо» логи в Loki (без прямого виклику з Quarkus)

Quarkus **не зобов’язаний** знати URL Loki. Типовий шлях:

1. Додаток лише **логує в консоль**.
2. **Alloy** (окремий контейнер або DaemonSet) має доступ до **логів контейнерів** або до **Docker socket** / **Kubernetes API** для discovery.
3. Alloy надсилає батчі на `http://loki:3100/loki/api/v1/push` (або еквівалент для вашої версії Loki).

Налаштування прав доступу (volumes, `docker` group, security context у K8s) — окрема операційна задача; на Linux для Docker Desktop / rootless режимів шляхи відрізняються.

## У цьому репозиторії (`quarkus-ms-gold-template`)

**Запуск розділено:**

1. **`make up-metrics`** — лише **Prometheus, Grafana, Loki, Alloy, Jaeger** (`deploy/docker-compose-metrics.yml`, окремий compose-проєкт **`metrics`**, щоб не перетинатися з `deploy/docker-compose.yml`). Без Quarkus і без окремої БД у цьому файлі.
2. **Застосунок окремо:** **`make up-prod`** (`deploy/docker-compose.yml`: PostgreSQL + native backend на **http://localhost:8080**) або **`./mvnw quarkus:dev`** на хості (**http://localhost:8080**).

| Сервіс | Порт (хост) | Призначення |
|--------|-------------|-------------|
| **Prometheus** | 9090 | Скрап метрик з `host.docker.internal:8080` (див. `deploy/monitoring/prometheus.yml`) |
| **Grafana** | 3000 | Дашборди, Explore (Loki datasource) |
| **Loki** | 3100 | API прийому логів |
| **Grafana Alloy** | 12345 | HTTP UI Alloy; збір логів з Docker → Loki |
| **Jaeger** | 16686 (+ 4317 OTLP) | Трейси; для бекенду в Docker налаштовано `host.docker.internal:4317` у `deploy/docker-compose.yml` |

- Конфіг Loki: `deploy/monitoring/loki-config.yaml` (образ `grafana/loki:3.7.1`; після оновлення з 2.x видаліть volume `loki_data`).
- Конфіг Alloy (River): `grafana/alloy/config.alloy` (`loki.source.docker` + `loki.write`).

**Важливо:** Alloy монтує **`/var/run/docker.sock`** — у Loki потрапляють логи **контейнерів** (наприклад `quarkus_prod_backend` після `make up-prod`). Процес **`quarkus:dev` лише на хості** у Loki через цей пайплайн **не** потрапляє.

## Мінімальні кроки для локального compose (загальна схема)

1. Підняти **Loki** + **Grafana Alloy** + **Grafana** (у нас — у `deploy/docker-compose-metrics.yml`).
2. У Grafana перевірити datasource **Loki** і **Explore** (наприклад `{job="docker"}` або ім’я контейнера).
3. Деталі синтаксису River — [документація Alloy](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.docker/).

## Зв’язок з курсом

- Розділ **14** (метрики, health) і **11** (production checklist) — спостережуваність; централізовані логи доповнюють **Prometheus + Grafana** і **OpenTelemetry / Jaeger**.

---

[← Документація observability](README.md) · [Курс Quarkus](../quarkus-course/README.md)
