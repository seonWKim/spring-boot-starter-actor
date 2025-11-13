# Task 8.1: Grafana Dashboard Templates

**Priority:** MEDIUM
**Estimated Effort:** 4-5 days
**Dependencies:** All metrics implemented
**Assignee:** AI Agent

---

## Objective

Create comprehensive Grafana dashboard templates for monitoring actor system metrics.

---

## Dashboards to Create

### 1. Actor System Overview Dashboard

**Panels:**
- Active Actors (Gauge)
- Messages Processed (Counter rate)
- Error Rate (Counter rate)
- Dead Letter Rate (Counter rate)
- Average Processing Time (Timer avg)
- System Uptime

**Layout:**
```
┌────────────────┬────────────────┬────────────────┐
│ Active Actors  │ Msg/sec        │ Error Rate     │
├────────────────┴────────────────┴────────────────┤
│ Message Processing Time (P50, P95, P99)          │
├───────────────────────────────────────────────────┤
│ Messages Processed Over Time (Graph)              │
├───────────────────────────────────────────────────┤
│ Error Rate Over Time (Graph)                      │
└───────────────────────────────────────────────────┘
```

### 2. Actor Metrics Dashboard

**Panels:**
- Mailbox Size by Actor
- Processing Time by Actor
- Error Count by Actor
- Messages Processed by Actor
- Actor Lifecycle Events

### 3. Dispatcher Metrics Dashboard

**Panels:**
- Active Threads
- Queue Size
- Thread Pool Utilization
- Tasks Completed Rate
- Tasks Rejected
- Dispatcher Configuration

### 4. Cluster Metrics Dashboard (if applicable)

**Panels:**
- Cluster Members
- Unreachable Members
- Shard Distribution
- Entity Count by Region
- Message Distribution

---

## Implementation Files

### Dashboard JSON Files
1. **`grafana/dashboards/actor-system-overview.json`**
2. **`grafana/dashboards/actor-metrics.json`**
3. **`grafana/dashboards/dispatcher-metrics.json`**
4. **`grafana/dashboards/cluster-metrics.json`**

### Provisioning Configuration
5. **`grafana/provisioning/dashboards/dashboards.yml`**
6. **`grafana/provisioning/datasources/datasources.yml`**

---

## Dashboard Configuration

### Datasource Configuration
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

### Dashboard Provisioning
```yaml
apiVersion: 1

providers:
  - name: 'Actor System Dashboards'
    orgId: 1
    folder: 'Actor System'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    options:
      path: /etc/grafana/provisioning/dashboards
```

---

## Example Queries

### Active Actors
```promql
actor_system_active{system="application"}
```

### Message Processing Rate
```promql
rate(actor_messages_processed_total[5m])
```

### Error Rate
```promql
rate(actor_errors_total[5m])
```

### P95 Processing Time
```promql
histogram_quantile(0.95, 
  rate(actor_processing_time_bucket[5m])
)
```

### Mailbox Size
```promql
actor_mailbox_size{actor_class="OrderActor"}
```

### Thread Pool Utilization
```promql
dispatcher_threads_active / dispatcher_threads_total * 100
```

---

## Alert Rules

### High Error Rate
```yaml
- alert: HighActorErrorRate
  expr: rate(actor_errors_total[5m]) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High error rate in actor system"
    description: "Error rate is {{ $value }} errors/sec"
```

### High Mailbox Size
```yaml
- alert: HighMailboxSize
  expr: actor_mailbox_size > 1000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High mailbox size for actor {{ $labels.actor_path }}"
    description: "Mailbox size is {{ $value }}"
```

### Low Active Actors
```yaml
- alert: NoActiveActors
  expr: actor_system_active == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "No active actors in system"
    description: "Actor system has no active actors"
```

---

## Docker Compose Setup

```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./prometheus/alerts.yml:/etc/prometheus/alerts.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
    depends_on:
      - prometheus

  alertmanager:
    image: prom/alertmanager:latest
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml
```

---

## Documentation Files

### 1. Dashboard Import Guide
**File:** `docs/grafana/importing-dashboards.md`

Topics:
- How to import dashboard JSON
- Configuring datasources
- Customizing dashboards
- Setting up alerts

### 2. Metrics Guide
**File:** `docs/grafana/metrics-guide.md`

Topics:
- Available metrics
- Query examples
- Best practices
- Common dashboard patterns

### 3. Troubleshooting Guide
**File:** `docs/grafana/troubleshooting.md`

Topics:
- Dashboard not showing data
- Missing metrics
- Prometheus configuration
- Common issues

---

## Testing

### Manual Testing
1. Start Prometheus and Grafana
2. Import dashboards
3. Generate actor load
4. Verify metrics appear in dashboards
5. Test alerts

### Automated Testing
- Screenshot testing for dashboard rendering
- Query validation
- Alert rule validation

---

## Acceptance Criteria

- [ ] 4 comprehensive dashboards created
- [ ] All metrics represented in dashboards
- [ ] Dashboard JSON files validated
- [ ] Provisioning configuration works
- [ ] Alert rules defined
- [ ] Docker Compose setup provided
- [ ] Documentation complete
- [ ] Import guide written
- [ ] Screenshots included
- [ ] Example queries documented

---

## Dashboard Screenshots

Include screenshots of each dashboard in documentation:
- `docs/grafana/screenshots/actor-system-overview.png`
- `docs/grafana/screenshots/actor-metrics.png`
- `docs/grafana/screenshots/dispatcher-metrics.png`
- `docs/grafana/screenshots/cluster-metrics.png`

---

## Notes

- Use Grafana dashboard best practices
- Include variable templates for filtering
- Add links between related dashboards
- Consider dark mode compatibility
- Test with realistic data volumes
