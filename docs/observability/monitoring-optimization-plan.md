# План оптимального моніторингу (максимум корисних даних)

**Статус впровадження (шаблон репозиторію):** увімкнено Vert.x / Netty / virtual-threads binders у `application.properties`; семплінг трейсів для `%prod`; дашборд Grafana доповнено панелями SLI + connections + Netty; Jaeger `tracesToMetrics` оновлено; див. `metrics-inventory.md`.

Ціль: отримати **повну картину** — метрики (SLI + інфраструктура), трейси, логи — з **зрозумілими зв’язками** у Grafana (Explore + дашборди) і без зайвої кардинальності.

Орієнтири в документації:

- [Quarkus — Micrometer](https://quarkus.io/guides/telemetry-micrometer)
- [Vert.x — Micrometer metrics](https://vertx.io/docs/vertx-micrometer-metrics/java/)
- [Grafana — Prometheus / Loki / Jaeger](https://grafana.com/docs/grafana/latest/datasources/)

---

## 1. Принципи

| Принцип | Що робити |
|--------|-----------|
| **Один шар — одне питання** | SLI по REST (`http_server_requests_*`) окремо від транспорту (`http_server_*` / Vert.x) і від Netty/JVM. |
| **Кардинальність** | URI як шаблони (`/api/item/{id}`), `ignore-patterns` / `match-patterns` для health/metrics. |
| **Кореляція** | Один `traceId` у логах + трейс у Jaeger + (за наявності) exemplars у Prometheus. |
| **Режими збірки** | Перевіряти `/q/metrics` окремо для **JVM** і **native** — набір метрик може відрізнятися. |

---

## 2. Метрики Quarkus (Micrometer) — пріоритет №1

### 2.1 HTTP (продуктові SLI)

- [x] Переконатися, що в `/q/metrics` є **Micrometer HTTP server**: `http_server_requests_seconds_count` / `_sum` / `_max` (або актуальні імена з вашої версії Micrometer). *Перевірка: `metrics-inventory.md` + `curl /q/metrics`.*
- [ ] Якщо в native **немає** цих серій — зафіксувати в README і розглянути: оновлення Quarkus, окремий профіль збірки, або issue в Quarkus (це не Grafana).
- [ ] У `application.properties` налаштувати зменшення кардинальності:
  - `quarkus.micrometer.binder.http-server.match-patterns` — шаблони URI
  - `quarkus.micrometer.binder.http-server.ignore-patterns` — виключити шум
  - за потреби: `quarkus.micrometer.binder.http-server.suppress-non-application-uris` (вже часто `true`)

### 2.2 Додаткові біндери (максимум інфраструктурних даних)

- [x] Увімкнути **`quarkus.micrometer.binder.vertx.enabled=true`** — метрики рівня Vert.x (з’єднання, активні запити, тощо; імена у Prometheus перевірити на `/q/metrics`).
- [x] Увімкнути **`quarkus.micrometer.binder.netty.enabled=true`** — Netty (пам’ять, пули) для діагностики під навантаженням.
- [x] За наявності вихідних HTTP-клієнтів — переконатися, що **`quarkus.micrometer.binder.http-client`** увімкнений (за замовчуванням залежить від REST client). *REST client у шаблоні не використовується — N/A.*

### 2.3 JVM / процес / система

- [x] Залишити увімкненими **JVM** та **system** binders (типово вже активні).
- [x] Для Java 21+ за потреби — **virtual threads** metrics (`quarkus.micrometer.binder.virtual-threads`). *Увімкнено (`true`), Java 25.*

### 2.4 Експорт і endpoint

- [x] Підтвердити шлях **`/q/metrics`** (або management interface, якщо винесете metrics окремо — [management interface](https://quarkus.io/guides/management-interface-reference)).
- [ ] За потреби — JSON-експорт лише для дебагу (`quarkus.micrometer.export.json.enabled`).

---

## 3. Трейси (OpenTelemetry → Jaeger)

- [x] Узгодити **`quarkus.otel.service.name`** з очікуваннями в Jaeger/Grafana.
- [x] Перевірити семплінг: для прод — осмислений `traces sampler` (не 100% без потреби). *Задано `%prod`: `parentbased_traceidratio` + `arg=0.25`; dev/test: `always_on`.*
- [x] У Grafana: **Jaeger** datasource з **tracesToLogsV2** (Loki) і **tracesToMetrics** (Prometheus) — запити мають посилатися на **реальні** метрики у вашому експорті (оновлювати при зміні імен). *Додано SLI + transport queries з `job` label.*

---

## 4. Логи → Loki

- [x] У prod залишати **структурований JSON** (у вас уже закладено) — без зайвого `System.out`.
- [x] У Grafana Loki: **derived fields** (traceId → Jaeger), перевірити regex під реальний формат JSON.
- [x] Alloy: залишити збір Docker logs; за потреби додати парсинг/лейбли на стороні pipeline.

---

## 5. Prometheus

- [x] `scrape_interval` узгодити з Grafana datasource **`timeInterval`** (у вас 5s / 5s).
- [ ] За потреби — **recording rules** для важких запитів на дашбордах.
- [ ] Розглянути **exemplars**: якщо увімкнете експорт exemplars з додатку — налаштування в Grafana Prometheus datasource (`exemplarTraceIdDestinations` → Jaeger) почне працювати end-to-end.

---

## 6. Grafana

### 6.1 Дашборди

- [x] Панелі **RPS / латентність / помилки** — тільки з **`http_server_requests_*`** (коли з’являться у scrape). *Додано панель RPS з `http_server_requests_seconds_count`; латентність/histogram — за наявності `_bucket`.*
- [x] Окремі панелі: **Vert.x** (активні з’єднання, байти) та **Netty** (пам’ять) — після увімкнення біндерів. *Додано active connections + netty_allocator_pooled_arenas; байти залишились у верхніх панелях.*
- [x] Змінна **`instance`**: будувати з метрики, яка **завжди** є (`up{job=...}` або `process_cpu_usage`), а не з відсутньої серії.

### 6.2 Кореляції

- [ ] За потреби — **Explore** кореляції metrics ↔ logs ↔ traces (Grafana correlations), якщо потрібен єдиний UX понад поточні лінки.

---

## 7. Операційний рівень (хост / Docker)

- [ ] **journald**: ротація через `journald.conf` (`SystemMaxUse` тощо).
- [ ] Ліміти контейнерів (CPU/RAM) для Prometheus/Loki/Grafana — щоб стек не «валив» Docker під навантаженням.
- [ ] Для Alloy + journal (якщо колись перейдете): **group_add** `systemd-journal` + mount journal.

---

## 8. Порядок виконання (фази)

| Фаза | Дії | Результат |
|------|-----|-----------|
| **A** | Аудит `/q/metrics` (JVM + native окремо), таблиця «метрика → призначення» | Зрозуміло, чого бракує для req/s |
| **B** | Увімкнути Vert.x + Netty binders, перевірити кардинальність URI | Транспорт + пам’ять у Prometheus |
| **C** | Оновити Grafana панелі під фактичні імена; змінна instance | Немає «No data» через порожній label_values |
| **D** | Підлаштувати tracesToMetrics + derived fields | Клікабельні переходи trace ↔ logs ↔ metrics |
| **E** | (Опційно) exemplars + alerts | Глибша діагностика та сповіщення |

*Фази A–D частково закриті в репозиторії; A (native vs JVM) і кардинальність URI — залишити для ручної перевірки.*

---

## 9. Критерій «готово»

- На дашборді є **запити/с і латентність** (або обґрунтовано задокументовано, чому в native їх немає).
- Є **JVM + CPU + мережевий/транспортний** шар (Vert.x/Netty) без дублювання сенсу.
- **Логи** з traceId відкривають трейс у Jaeger; з трейса є перехід до логів/метрик.
- Prometheus target стабільно **up**, Grafana datasource **Save & test** OK.

Цей документ можна використовувати як чеклист; конкретні ключі `application.properties` див. у [Quarkus Micrometer — configuration](https://quarkus.io/guides/telemetry-micrometer).
