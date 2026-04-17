# Скіли та архітектурні терміни (gold template) — для інтеграції в резюме

Короткий збір **назв практик англійською / у стандартах індустрії** і **стеку**, без навчального тексту.

---

## Observability — повний збір сигналів (як це називається)

| Термін / практика | Стек у шаблоні |
|-------------------|----------------|
| **Metrics** (application + JVM) | Micrometer, Prometheus scrape `/q/metrics`, Grafana |
| **Logs** (centralized, container stdout) | Grafana Alloy → Loki, LogQL |
| **Distributed tracing** | OpenTelemetry OTLP → Jaeger |
| **Correlation** (trace ↔ logs ↔ metrics) | Grafana datasources, `tracesToLogsV2`, trace-to-metrics (feature toggle) |
| **Dashboards & config as code** | Grafana provisioning (datasources, dashboards JSON) |
| **Load / SLO testing** (дорожня карта шаблону) | k6 (сценарії, thresholds) |

Архітектурно це часто формулюють як: **full-stack observability**, **three pillars** (metrics, logs, traces), **GitOps-friendly observability config**.

---

## Стабільність БД і схеми (перетин з Hibernate ORM і Panache)

| Термін / практика | Суть |
|-------------------|------|
| **Database schema ownership** | Єдине джерело правди щодо DDL — **Flyway** (`db/migration`), не Hibernate `update`. |
| **Hibernate ORM schema management: `validate`** | У dev/test/prod: лише перевірка відповідності JPA-моделі й схеми БД; **без auto-DDL** у проді. |
| **Versioned migrations** | Flyway, порядок застосування відносно старту persistence (`migrate-at-start`). |
| **Panache** | **PanacheRepository** — шар доступу до даних; **Panache REST Data** (`PanacheRepositoryResource`) — CRUD REST за репозиторієм; за потреби **PanacheEntity** (active record) як патерн. |
| **ORM vs DDL розділення** | Зміни схеми — міграціями; Hibernate відповідає за **мапінг** і **валідацію** моделі до існуючої схеми. |

Для резюме: **Flyway + Hibernate `validate`**, **JPA / Hibernate ORM**, **Quarkus Panache** (repository + REST Data).

---

## REST API і Validation (ідіоматичні назви)

| Термін / практика | Суть |
|-------------------|------|
| **Jakarta REST (JAX-RS)** | Ресурси, HTTP-семантика, `@Path`, `@GET` / `@POST` / … |
| **Jakarta Bean Validation** | Декларативні обмеження: `@NotBlank`, `@Min`, `@Positive`, `@Size`, … |
| **Validation at API boundary** | `@Valid` на тілах запитів / DTO; помилки — **400 Bad Request** з constraint violations |
| **DTO / request models** | Окремі типи для вхідних даних (наприклад `ProductRequest`), відокремлення від entity |
| **REST Data Panache** | Генерований CRUD для сутностей за інтерфейсом ресурсу |

Для резюме: **Jakarta Validation (Bean Validation 3.x)**, **REST layer validation**, **JAX-RS / Quarkus REST**.

*(Якщо потрібен акцент на «graceful»: у документації API це часто перетинається з **consistent error responses** і коректними HTTP-кодами — це вже політика API, не окремий фреймворк.)*

---

## Контейнеризація (назви практик)

| Термін / практика | Суть |
|-------------------|------|
| **Docker Compose** | Декларативні стеки: застосунок + БД окремо від observability-стеку |
| **Multi-stack deployment** | Різні compose-файли / `-p` project name — ізоляція життєвого циклу |
| **Pinned image versions** | Фіксовані теги образів у compose |
| **Named volumes** | Персистентність даних (PostgreSQL, Loki, Prometheus, Grafana) |
| **Host gateway / scrape** | `host.docker.internal` для метрик процесу на хості з контейнера Prometheus |

Для резюме: **Docker**, **Docker Compose**, **container networking**, **12-factor–сумісний** деплой локально.

---

## Один рядок технологій (копіпаст у CV)

**Backend:** Quarkus, Jakarta EE / Jakarta REST, Hibernate ORM, Panache (Repository + REST Data), Flyway, Jakarta Validation.  
**Observability:** Micrometer, Prometheus, Grafana (provisioning), Loki, Grafana Alloy, OpenTelemetry, Jaeger.  
**Ops:** Docker Compose, Makefile; (дорожня карта) k6.  
**DB discipline:** schema migrations (Flyway), `validate` strategy, no prod auto-DDL.

---

*Оновлювати таблиці при зміні репозиторію.*

Internal Developer Portal (IDP)
