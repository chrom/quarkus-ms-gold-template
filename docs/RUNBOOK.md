# Incident Runbook: Quarkus MS Gold Template

This document provides a quick-reference guide for On-Call SREs and developers responding to Production incidents (e.g., SLA violation alerts).

## 1. Quick Diagnostics

When an alert triggers (e.g., Latency > 500ms or Availability < 99.9%):

1. **Check Dashboards FIRST:** 
   - Open Grafana → Dashboard: **Quarkus Cloud Template — Overview** (provisioned from `grafana/dashboards/quarkus-overview.json`).
   - Look at the `HTTP 5xx rate` and `p95 Latency` panels.
   - Look at the JVM Memory bounds in the `JVM / Micrometer` panel (check for GC pauses).
2. **Read the Logs:**
   - Go to Grafana Explore → Loki.
   - Query: `{container="quarkus-ms-gold-template"} | json | level="ERROR"`
3. **Trace the Slow Requests:**
   - If latency is spiking, find a specific slow request trace ID from Loki and paste it into Jaeger. This will show exactly where the time is spent (e.g., DB queries vs Network overhead).

## 2. Emergency Rollback

If a bad deployment caused the issue and the system is actively failing, **rollback immediately** rather than debugging live.

**Kubernetes Rollback Command:**
```bash
kubectl rollout undo deployment/quarkus-ms-gold-template -n production
```
*Wait 30 seconds and check Grafana to see if metrics recover.*

## 3. Communication Matrix

If the incident lasts longer than 15 minutes, notify the relevant stakeholders:

- **Primary Contact / On-Call:** `@on-call-backend` (Slack channel `#alerts-backend`)
- **Database Team (if DB issues):** `@dba-team` (Slack channel `#alerts-db`)
- **Management Update:** Create an incident thread in `#incidents-major` with the template:
  > **Incident:** Quarkus Latency Spike  
  > **Status:** Investigating / Mitigating  
  > **Impact:** Users experiencing timeouts on checkout.

## 4. Known Issues & Mitigation

- **High DB Connection Exhaustion:** 
  - *Symptom:* Logs show Agroal Connection Pool timeouts.
  - *Action:* Check if PostgreSQL is locked by a long-running transaction. If necessary, slightly increase `quarkus.datasource.jdbc.max-size` via hot-redeploy config map, but be careful not to overload the DB.
