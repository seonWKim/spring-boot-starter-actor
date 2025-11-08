# Log Aggregation Guide

Guide for centralized logging in Kubernetes for Spring Boot Starter Actor applications.

## Table of Contents

1. [Logging Architecture](#logging-architecture)
2. [ELK Stack (Elasticsearch, Logstash, Kibana)](#elk-stack)
3. [Loki with Grafana](#loki-with-grafana)
4. [Fluent Bit](#fluent-bit)
5. [Cloud Provider Solutions](#cloud-provider-solutions)
6. [Log Management Best Practices](#log-management-best-practices)

## Logging Architecture

### Current Setup

Spring Boot Starter Actor logs to stdout/stderr:

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    org.apache.pekko: INFO
    io.github.seonwkim: DEBUG
```

### Recommended Architecture

```
┌─────────────┐
│   Pod       │
│  ┌────────┐ │
│  │  App   │ │──▶ stdout/stderr
│  └────────┘ │
└─────────────┘
       │
       ▼
┌─────────────┐
│ Log Shipper │ (Fluent Bit / Fluentd)
│ (DaemonSet) │
└─────────────┘
       │
       ▼
┌─────────────┐
│   Storage   │ (Elasticsearch / Loki / Cloud)
└─────────────┘
       │
       ▼
┌─────────────┐
│     UI      │ (Kibana / Grafana / Cloud Console)
└─────────────┘
```

## ELK Stack

### Installation

**Using Helm:**

```bash
# Add Elastic repo
helm repo add elastic https://helm.elastic.co
helm repo update

# Install Elasticsearch
helm install elasticsearch elastic/elasticsearch \
  --namespace logging \
  --create-namespace \
  --set replicas=3 \
  --set minimumMasterNodes=2 \
  --set resources.requests.memory=2Gi \
  --set resources.requests.cpu=1000m

# Install Kibana
helm install kibana elastic/kibana \
  --namespace logging \
  --set elasticsearchHosts="http://elasticsearch-master:9200"

# Install Filebeat (log shipper)
helm install filebeat elastic/filebeat \
  --namespace logging \
  --set daemonset.enabled=true
```

### Filebeat Configuration

Create `filebeat-config.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
  namespace: logging
data:
  filebeat.yml: |
    filebeat.inputs:
    - type: container
      paths:
        - /var/log/containers/*spring-actor*.log
      processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/log/containers/"
      
      # Parse JSON logs
      - decode_json_fields:
          fields: ["message"]
          target: "json"
          overwrite_keys: true
      
      # Add custom fields
      - add_fields:
          target: labels
          fields:
            app: spring-actor
            environment: ${ENVIRONMENT}
    
    # Output to Elasticsearch
    output.elasticsearch:
      hosts: ["elasticsearch-master:9200"]
      indices:
        - index: "spring-actor-%{+yyyy.MM.dd}"
          when.equals:
            kubernetes.labels.app: "spring-actor"
    
    # Setup index lifecycle management
    setup.ilm.enabled: true
    setup.ilm.rollover_alias: "spring-actor"
    setup.ilm.pattern: "{now/d}-000001"
    
    # Kibana dashboard setup
    setup.kibana:
      host: "kibana:5601"
    
    # Logging
    logging.level: info
    logging.to_files: true
    logging.files:
      path: /var/log/filebeat
      name: filebeat
      keepfiles: 7
      permissions: 0644
---
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: filebeat
  namespace: logging
spec:
  selector:
    matchLabels:
      app: filebeat
  template:
    metadata:
      labels:
        app: filebeat
    spec:
      serviceAccountName: filebeat
      terminationGracePeriodSeconds: 30
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      containers:
      - name: filebeat
        image: docker.elastic.co/beats/filebeat:8.11.0
        args: [
          "-c", "/etc/filebeat.yml",
          "-e",
        ]
        env:
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        - name: ENVIRONMENT
          value: "production"
        securityContext:
          runAsUser: 0
        resources:
          limits:
            memory: 200Mi
          requests:
            cpu: 100m
            memory: 100Mi
        volumeMounts:
        - name: config
          mountPath: /etc/filebeat.yml
          readOnly: true
          subPath: filebeat.yml
        - name: data
          mountPath: /usr/share/filebeat/data
        - name: varlibdockercontainers
          mountPath: /var/lib/docker/containers
          readOnly: true
        - name: varlog
          mountPath: /var/log
          readOnly: true
      volumes:
      - name: config
        configMap:
          defaultMode: 0640
          name: filebeat-config
      - name: varlibdockercontainers
        hostPath:
          path: /var/lib/docker/containers
      - name: varlog
        hostPath:
          path: /var/log
      - name: data
        hostPath:
          path: /var/lib/filebeat-data
          type: DirectoryOrCreate
```

### Elasticsearch Index Lifecycle Management

```yaml
# ILM policy for log retention
apiVersion: v1
kind: ConfigMap
metadata:
  name: elasticsearch-ilm-policy
  namespace: logging
data:
  spring-actor-policy.json: |
    {
      "policy": {
        "phases": {
          "hot": {
            "actions": {
              "rollover": {
                "max_size": "50GB",
                "max_age": "1d"
              }
            }
          },
          "warm": {
            "min_age": "7d",
            "actions": {
              "shrink": {
                "number_of_shards": 1
              },
              "forcemerge": {
                "max_num_segments": 1
              }
            }
          },
          "cold": {
            "min_age": "30d",
            "actions": {
              "freeze": {}
            }
          },
          "delete": {
            "min_age": "90d",
            "actions": {
              "delete": {}
            }
          }
        }
      }
    }
```

### Kibana Dashboards

Access Kibana:
```bash
kubectl port-forward -n logging svc/kibana 5601:5601
open http://localhost:5601
```

**Create index pattern:**
1. Go to Stack Management → Index Patterns
2. Create pattern: `spring-actor-*`
3. Select timestamp field: `@timestamp`

**Sample queries:**
```
# Errors in last hour
kubernetes.labels.app:"spring-actor" AND level:"ERROR"

# Cluster events
kubernetes.labels.app:"spring-actor" AND message:"cluster"

# Slow requests (> 1s)
kubernetes.labels.app:"spring-actor" AND duration:>1000

# Specific pod logs
kubernetes.pod.name:"spring-actor-prod-abc123"
```

## Loki with Grafana

Loki is a lightweight alternative to Elasticsearch, designed for Grafana.

### Installation

```bash
# Add Grafana repo
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# Install Loki
helm install loki grafana/loki-stack \
  --namespace logging \
  --create-namespace \
  --set loki.persistence.enabled=true \
  --set loki.persistence.size=50Gi \
  --set promtail.enabled=true \
  --set grafana.enabled=true \
  --set grafana.persistence.enabled=true
```

### Promtail Configuration

Promtail ships logs to Loki:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: promtail-config
  namespace: logging
data:
  promtail.yaml: |
    server:
      http_listen_port: 9080
      grpc_listen_port: 0

    positions:
      filename: /tmp/positions.yaml

    clients:
      - url: http://loki:3100/loki/api/v1/push

    scrape_configs:
    - job_name: kubernetes-pods
      kubernetes_sd_configs:
      - role: pod
      
      relabel_configs:
      # Only scrape spring-actor pods
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: spring-actor
      
      # Add namespace label
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
      
      # Add pod name label
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
      
      # Add container name
      - source_labels: [__meta_kubernetes_pod_container_name]
        target_label: container
      
      # Use pod label for job
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: job
      
      # Set log path
      - source_labels: [__meta_kubernetes_pod_uid, __meta_kubernetes_pod_container_name]
        target_label: __path__
        separator: /
        replacement: /var/log/pods/*$1/*.log
      
      pipeline_stages:
      # Parse JSON logs
      - json:
          expressions:
            timestamp: time
            level: level
            logger: logger
            message: message
            thread: thread
      
      # Extract timestamp
      - timestamp:
          source: timestamp
          format: RFC3339
      
      # Add labels
      - labels:
          level:
          logger:
      
      # Drop debug logs (optional)
      - match:
          selector: '{level="DEBUG"}'
          action: drop
```

### Grafana Log Queries

In Grafana Explore:

```logql
# All spring-actor logs
{job="spring-actor"}

# Error logs only
{job="spring-actor"} |= "ERROR"

# Cluster-related logs
{job="spring-actor"} |~ "cluster|Cluster"

# Logs from specific pod
{job="spring-actor", pod="spring-actor-prod-abc123"}

# Rate of errors
rate({job="spring-actor"} |= "ERROR" [5m])

# Top 10 error messages
topk(10, 
  sum by (message) (
    count_over_time({job="spring-actor"} |= "ERROR" [1h])
  )
)
```

### Loki Retention

Configure in `values.yaml`:

```yaml
loki:
  config:
    table_manager:
      retention_deletes_enabled: true
      retention_period: 720h  # 30 days
    
    limits_config:
      retention_period: 720h
      
    compactor:
      working_directory: /data/compactor
      shared_store: filesystem
      compaction_interval: 10m
      retention_enabled: true
      retention_delete_delay: 2h
      retention_delete_worker_count: 150
```

## Fluent Bit

Fluent Bit is a lightweight log forwarder (alternative to Fluentd).

### Installation

```bash
helm repo add fluent https://fluent.github.io/helm-charts
helm install fluent-bit fluent/fluent-bit \
  --namespace logging \
  --create-namespace
```

### Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: logging
data:
  fluent-bit.conf: |
    [SERVICE]
        Daemon Off
        Flush 1
        Log_Level info
        Parsers_File parsers.conf
        HTTP_Server On
        HTTP_Listen 0.0.0.0
        HTTP_Port 2020
        Health_Check On

    [INPUT]
        Name tail
        Path /var/log/containers/*spring-actor*.log
        Parser docker
        Tag kube.*
        Mem_Buf_Limit 5MB
        Skip_Long_Lines On

    [FILTER]
        Name kubernetes
        Match kube.*
        Kube_URL https://kubernetes.default.svc:443
        Kube_CA_File /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File /var/run/secrets/kubernetes.io/serviceaccount/token
        Kube_Tag_Prefix kube.var.log.containers.
        Merge_Log On
        Keep_Log Off
        K8S-Logging.Parser On
        K8S-Logging.Exclude On

    [FILTER]
        Name parser
        Match kube.*
        Key_Name log
        Parser json
        Reserve_Data On

    [OUTPUT]
        Name es
        Match kube.*
        Host elasticsearch-master.logging
        Port 9200
        Logstash_Format On
        Logstash_Prefix spring-actor
        Retry_Limit False
        Type _doc

  parsers.conf: |
    [PARSER]
        Name docker
        Format json
        Time_Key time
        Time_Format %Y-%m-%dT%H:%M:%S.%L%z

    [PARSER]
        Name json
        Format json
        Time_Key time
        Time_Format %Y-%m-%d %H:%M:%S.%L
```

## Cloud Provider Solutions

### AWS CloudWatch

**Using Fluent Bit to CloudWatch:**

```yaml
# Add IAM permissions to service account
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluent-bit
  namespace: logging
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/FluentBitRole

---
# Fluent Bit output to CloudWatch
[OUTPUT]
    Name cloudwatch_logs
    Match kube.*
    region us-east-1
    log_group_name /aws/eks/spring-actor/cluster
    log_stream_prefix from-fluent-bit-
    auto_create_group true
```

**Query CloudWatch Logs:**
```bash
aws logs filter-log-events \
  --log-group-name /aws/eks/spring-actor/cluster \
  --filter-pattern "ERROR" \
  --start-time $(date -d '1 hour ago' +%s)000
```

### GCP Cloud Logging

**Using Fluentd:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluentd-config
  namespace: logging
data:
  fluent.conf: |
    <source>
      @type tail
      path /var/log/containers/*spring-actor*.log
      pos_file /var/log/fluentd-containers.log.pos
      tag kubernetes.*
      read_from_head true
      <parse>
        @type json
        time_format %Y-%m-%dT%H:%M:%S.%NZ
      </parse>
    </source>

    <match kubernetes.**>
      @type google_cloud
      use_metadata_service true
      buffer_chunk_limit 512K
      flush_interval 5s
      max_retry_wait 30
      disable_retry_limit
      num_threads 2
    </match>
```

**Query Cloud Logging:**
```bash
gcloud logging read \
  'resource.type="k8s_container"
   AND resource.labels.cluster_name="spring-actor-cluster"
   AND resource.labels.namespace_name="spring-actor"
   AND severity="ERROR"' \
  --limit 50 \
  --format json
```

### Azure Monitor

**Using Container Insights:**

```bash
# Enable Container Insights
az aks enable-addons \
  --resource-group myResourceGroup \
  --name myAKSCluster \
  --addons monitoring
```

**Query with KQL:**
```kql
ContainerLog
| where TimeGenerated > ago(1h)
| where ContainerName contains "spring-actor"
| where LogEntry contains "ERROR"
| project TimeGenerated, LogEntry, Computer
| order by TimeGenerated desc
| take 100
```

## Log Management Best Practices

### Structured Logging

Use JSON format for better parsing:

```java
// logback-spring.xml
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"spring-actor","environment":"${ENVIRONMENT}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE" />
    </root>
</configuration>
```

### Correlation IDs

Add trace/correlation IDs for request tracking:

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        MDC.put("correlationId", correlationId);
        response.setHeader("X-Correlation-ID", correlationId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### Log Levels

Use appropriate log levels:

```yaml
logging:
  level:
    root: INFO                    # General logs
    org.apache.pekko: INFO        # Pekko framework
    org.apache.pekko.cluster: DEBUG  # Cluster formation (useful)
    io.github.seonwkim: DEBUG     # Your application
    org.springframework.web: INFO # Spring Web
    
    # Reduce noise from specific loggers
    org.springframework.web.servlet.mvc: WARN
    org.hibernate.SQL: WARN
```

### Sampling

For high-volume logs, implement sampling:

```java
@Component
public class SamplingLogger {
    
    private static final Logger log = LoggerFactory.getLogger(SamplingLogger.class);
    private final AtomicInteger counter = new AtomicInteger(0);
    private final int sampleRate = 100; // Log 1 in 100
    
    public void logIfSampled(String message) {
        if (counter.incrementAndGet() % sampleRate == 0) {
            log.info(message);
        }
    }
}
```

### Log Retention Policy

Define clear retention periods:

| Environment | Retention | Reason |
|-------------|-----------|--------|
| Development | 7 days | Short-term debugging |
| Staging | 30 days | Testing and validation |
| Production | 90 days | Compliance, audit trail |
| Audit logs | 1+ year | Regulatory requirements |

### Cost Management

**Strategies to reduce logging costs:**

1. **Filter at source:**
```yaml
# In Fluent Bit
[FILTER]
    Name grep
    Match kube.*
    Exclude log .*health.*  # Drop health check logs
```

2. **Compress logs:**
```yaml
# Elasticsearch
PUT _cluster/settings
{
  "persistent": {
    "indices.codec": "best_compression"
  }
}
```

3. **Use cheaper storage for old logs:**
```yaml
# Move to cold storage after 30 days
# Delete after 90 days
```

### Alerting on Logs

**Elasticsearch Watcher:**
```json
{
  "trigger": {
    "schedule": {
      "interval": "5m"
    }
  },
  "input": {
    "search": {
      "request": {
        "indices": ["spring-actor-*"],
        "body": {
          "query": {
            "bool": {
              "must": [
                {"match": {"level": "ERROR"}},
                {"range": {"@timestamp": {"gte": "now-5m"}}}
              ]
            }
          }
        }
      }
    }
  },
  "condition": {
    "compare": {
      "ctx.payload.hits.total": {
        "gt": 10
      }
    }
  },
  "actions": {
    "send_slack": {
      "webhook": {
        "url": "https://hooks.slack.com/services/YOUR/WEBHOOK/URL",
        "body": "High error rate detected: {{ctx.payload.hits.total}} errors in 5 minutes"
      }
    }
  }
}
```

**Loki alerts (via Prometheus):**
```yaml
groups:
- name: logs
  rules:
  - alert: HighErrorRate
    expr: |
      sum(rate({job="spring-actor"} |= "ERROR" [5m])) > 10
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "High error rate in logs"
      description: "Error rate is {{ $value }} per second"
```

## Troubleshooting

### Logs not appearing

```bash
# Check log shipper is running
kubectl get pods -n logging

# Check log shipper logs
kubectl logs -n logging -l app=fluent-bit

# Verify pod is logging to stdout
kubectl logs -n spring-actor <pod-name>

# Check log file exists on node
kubectl exec -n logging <fluent-bit-pod> -- ls -la /var/log/containers/ | grep spring-actor
```

### High log volume

```bash
# Check volume per pod
kubectl logs -n spring-actor <pod-name> --since=1h | wc -l

# Identify noisy loggers
kubectl logs -n spring-actor <pod-name> --since=1h | awk '{print $4}' | sort | uniq -c | sort -rn | head -10
```

### Missing fields in logs

```bash
# Test parser
kubectl exec -n logging <fluent-bit-pod> -- \
  fluent-bit -c /fluent-bit/etc/fluent-bit.conf --dry-run

# Check JSON format
kubectl logs -n spring-actor <pod-name> | head -1 | jq .
```

---

For more information, see:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Operations Runbook](OPERATIONS_RUNBOOK.md)
- [Cost Optimization](COST_OPTIMIZATION.md)
