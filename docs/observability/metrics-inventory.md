# Інвентар метрик (орієнтир для Grafana / Prometheus)

Після змін з `monitoring-optimization-plan.md` перевіряйте фактичний експорт:

```bash
curl -sS http://localhost:8080/q/metrics | rg '^(# TYPE|http_|vertx_|netty_|jvm_|process_)' | head -80
```

У **JVM** і **native** набори можуть відрізнятися — порівнюйте обидва режими.

## Шари

| Шар | Приклади імен (Prometheus) | Призначення в Grafana |
|-----|---------------------------|------------------------|
| **Micrometer HTTP (SLI)** | `http_server_requests_seconds_count`, `_sum`, `_bucket` | RPS, латентність, помилки по `uri` / `outcome` |
| **Транспорт (Vert.x stack)** | `http_server_bytes_written_count`, `http_server_active_connections` | Навантаження по байтах і з’єднаннях |
| **Netty** | `netty_allocator_pooled_arenas`, інші `netty_*` | Пам’ять і пули буферів |
| **JVM / процес** | `jvm_memory_used_bytes`, `process_cpu_usage` | Ресурси застосунку |

Якщо **`http_server_requests_seconds_*`** відсутні у вашому білді, див. коментар у плані моніторингу та issue Quarkus/native; до тих пір використовуйте транспортні метрики як проксі.
