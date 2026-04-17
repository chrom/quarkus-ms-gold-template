# Health checks: liveness, readiness, startup

## Навіщо цей документ

**Health** — це не «ще одна метрика», а **відповіді для оркестратора** (Kubernetes, Nomad, cloud LB): чи перезапускати процес, чи пускати на нього **користувацький** трафік, чи дочекатися **довгого старту** (cold start, Native).

Теорія та best practices курсу — у **[Розділі 14](../quarkus-course/14-health-metrics-openapi.md)**. Тут — **що вже є в репозиторії** і **план подальших кроків** у тому ж дусі, що й [logging-loki-alloy.md](logging-loki-alloy.md).

---

## Роль кожного типу перевірки

| Тип | Питання | Типова дія оркестратора |
|-----|---------|-------------------------|
| **Liveness** | Процес **живий** (не завис)? | Перезапуск пода |
| **Readiness** | Чи можна **зараз** слати **користувацький** трафік? | Зняти/додати под у балансувальник |
| **Startup** | Чи **закінчився** довгий старт? | Дати час без хибних liveness-restart |

У **Quarkus** (SmallRye Health): типові шляхи **`/q/health/live`**, **`/q/health/ready`**, **`/q/health/started`** (точні імена груп звіряйте з [Quarkus SmallRye Health](https://quarkus.io/guides/smallrye-health) для вашої версії).

---

## Відповідність Kubernetes ↔ endpoints

| Probe у Pod | Endpoint у Quarkus (зазвичай) |
|---------------|----------------------------------|
| `livenessProbe` | `/q/health/live` |
| `readinessProbe` | `/q/health/ready` |
| `startupProbe` | `/q/health/started` *(якщо використовуєте startup-групу)* |

**Правило:** liveness — **мінімум логіки** (без БД і важких зовнішніх викликів), readiness — **критичні залежності** для обробки запитів (наприклад БД) з **обмеженим таймаутом**.

---

## У цьому репозиторії (`quarkus-ms-gold-template`)

| Що | Де |
|----|-----|
| Розширення | `quarkus-smallrye-health` у `pom.xml` |
| **Liveness** | `org.acme.health.LivenessHealthCheck` — мінімальна перевірка «живий» |
| **Readiness** | `org.acme.health.DatabaseReadinessHealthCheck` — `Connection.isValid(5s)` до PostgreSQL |
| Тести REST | `HealthEndpointTest`: `/q/health/live` та `/q/health/ready` (очікується `UP` і згадка `postgresql` на ready) |

Локальна перевірка без K8s:

```bash
curl -s http://localhost:8080/q/health/live
curl -s http://localhost:8080/q/health/ready
```

Після **`make up-prod`** або **`quarkus:dev`** — **`http://localhost:8080`** (моніторинг окремо: `make up-metrics`).

---

## План (що вже зроблено і що за бажанням)

### Зроблено (база)

- [x] Підключено **SmallRye Health**.
- [x] Розділено **liveness** (легка) і **readiness** (БД з таймаутом **5 с**).
- [x] **Інтеграційні тести** на статуси live/ready.

### Наступні кроки (опційно, за потреби продакшену)

1. **Startup probe** — якщо **Native** або важкі залежності дають старт **довше за liveness `initialDelaySeconds`**: додати `@Startup` health check або налаштувати `/q/health/started` і **startupProbe** у Kubernetes (деталі — Розділ 14).
2. **Приклад маніфесту K8s** — фрагмент `Deployment` з `livenessProbe` / `readinessProbe` / `startupProbe` під ці URL (можна покласти в `docs/` або `k8s/` як референс).
3. **Безпека** — health не повинен у публічному інтернеті віддавати **зайві деталі** помилок; за потреби обмежити на ingress або окремий admin-порт (див. Розділ 14).
4. **Grafana / Prometheus** — health **не** замінює метрики; для SLO використовуються **метрики** та алерти; readiness — лише для **маршрутизації трафіку**.

---

## Зв’язок з курсом і чеклістом

- Розділ **14** — [Health, Metrics, OpenAPI](../quarkus-course/14-health-metrics-openapi.md).
- Розділ **11** — [Production checklist](../quarkus-course/11-production-checklist.md) (health: live vs ready; startup probe).

---

[← Observability](README.md) · [Курс Quarkus](../quarkus-course/README.md)
