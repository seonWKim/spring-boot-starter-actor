# Canary Deployment Guide

Deploy new versions safely with canary releases - gradually shift traffic to the new version while monitoring for issues.

## What is Canary Deployment?

A canary deployment releases a new version to a small subset of users before rolling it out to everyone. If the canary shows problems, you can quickly roll back with minimal impact.

```
┌─────────────────────────────────────┐
│         Load Balancer               │
└──────────┬──────────────────┬───────┘
           │                  │
     90%   │            10%   │
           ▼                  ▼
    ┌──────────┐      ┌──────────┐
    │  Stable  │      │  Canary  │
    │ (v1.0.0) │      │ (v1.1.0) │
    │ 5 pods   │      │ 1 pod    │
    └──────────┘      └──────────┘
```

## Quick Start

### 1. Deploy Stable Version

```bash
# Deploy current stable version
kubectl apply -k overlays/prod
```

### 2. Deploy Canary

```bash
# Deploy canary version (1 pod, 10% traffic)
cd example/kubernetes/overlays/canary

# Update image tag to new version
vim kustomization.yaml  # Change newTag to v1.1.0

# Deploy canary
kubectl apply -k .
```

### 3. Monitor Canary

```bash
# Check canary pod
kubectl get pods -n spring-actor -l version=canary

# Monitor metrics in Grafana
# Compare error rates, latency between stable and canary

# Check canary logs
kubectl logs -f -n spring-actor -l version=canary
```

### 4. Gradually Increase Traffic

```bash
# If canary looks good, scale up gradually
kubectl scale deployment spring-actor-canary --replicas=2 -n spring-actor

# Monitor for 15-30 minutes
# Check:
# - Error rate
# - Latency
# - Memory/CPU usage
# - Cluster health

# Continue scaling
kubectl scale deployment spring-actor-canary --replicas=3 -n spring-actor
```

### 5. Complete Rollout or Rollback

**Option A: Complete rollout (canary is healthy)**
```bash
# Update stable deployment to canary version
cd overlays/prod
vim kustomization.yaml  # Update newTag to v1.1.0
kubectl apply -k .

# Wait for stable to roll out
kubectl rollout status deployment/spring-actor-prod -n spring-actor

# Delete canary
kubectl delete -k overlays/canary
```

**Option B: Rollback (canary has issues)**
```bash
# Simply delete canary deployment
kubectl delete -k overlays/canary

# All traffic returns to stable version
```

## Traffic Splitting Strategies

### Using Istio

For precise traffic control:

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: spring-actor
  namespace: spring-actor
spec:
  hosts:
  - spring-actor
  http:
  - match:
    - headers:
        x-canary:
          exact: "true"
    route:
    - destination:
        host: spring-actor-canary
        subset: canary
  - route:
    - destination:
        host: spring-actor-prod
        subset: stable
      weight: 90
    - destination:
        host: spring-actor-canary
        subset: canary
      weight: 10
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: spring-actor
  namespace: spring-actor
spec:
  host: spring-actor
  subsets:
  - name: stable
    labels:
      version: stable
  - name: canary
    labels:
      version: canary
```

### Using NGINX Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-actor-canary
  namespace: spring-actor
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"  # 10% to canary
spec:
  ingressClassName: nginx
  rules:
  - host: spring-actor.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: spring-actor-canary
            port:
              number: 80
```

Gradually increase weight:
```bash
kubectl patch ingress spring-actor-canary -n spring-actor \
  -p '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"25"}}}'
```

### Using Flagger (GitOps)

Automated canary with Flagger:

```yaml
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: spring-actor
  namespace: spring-actor
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-actor-prod
  
  service:
    port: 80
    targetPort: 8080
  
  analysis:
    interval: 1m
    threshold: 5
    maxWeight: 50
    stepWeight: 10
    
    metrics:
    - name: request-success-rate
      thresholdRange:
        min: 99
      interval: 1m
    
    - name: request-duration
      thresholdRange:
        max: 500
      interval: 1m
    
    webhooks:
    - name: load-test
      url: http://flagger-loadtester.test/
      timeout: 5s
      metadata:
        cmd: "hey -z 1m -q 10 -c 2 http://spring-actor-canary.spring-actor/"
```

## Monitoring Canary

### Key Metrics to Watch

| Metric | Threshold | Action if Exceeded |
|--------|-----------|-------------------|
| Error rate | > 1% | Rollback immediately |
| P95 latency | > 500ms | Investigate, consider rollback |
| P99 latency | > 1s | Investigate |
| Memory usage | > 85% | Monitor closely |
| Cluster health | Unreachable members | Rollback |

### Grafana Queries

**Error rate comparison:**
```promql
# Stable error rate
sum(rate(http_server_requests_seconds_count{
  namespace="spring-actor",
  version="stable",
  status=~"5.."
}[5m])) / sum(rate(http_server_requests_seconds_count{
  namespace="spring-actor",
  version="stable"
}[5m]))

# Canary error rate
sum(rate(http_server_requests_seconds_count{
  namespace="spring-actor",
  version="canary",
  status=~"5.."
}[5m])) / sum(rate(http_server_requests_seconds_count{
  namespace="spring-actor",
  version="canary"
}[5m]))
```

**Latency comparison:**
```promql
# Stable P95 latency
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{
    namespace="spring-actor",
    version="stable"
  }[5m])
)

# Canary P95 latency
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket{
    namespace="spring-actor",
    version="canary"
  }[5m])
)
```

### Automated Monitoring Script

```bash
#!/bin/bash
# monitor-canary.sh

NAMESPACE="spring-actor"
STABLE_LABEL="version=stable"
CANARY_LABEL="version=canary"
DURATION=1800  # 30 minutes

echo "🚀 Monitoring canary deployment for $DURATION seconds..."

start_time=$(date +%s)
while [ $(($(date +%s) - start_time)) -lt $DURATION ]; do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "⏰ $(date)"
  
  # Check pod status
  echo ""
  echo "📊 Pod Status:"
  kubectl get pods -n $NAMESPACE -l $CANARY_LABEL
  
  # Check error rate
  echo ""
  echo "📈 Error Rate (last 5 min):"
  CANARY_POD=$(kubectl get pods -n $NAMESPACE -l $CANARY_LABEL -o jsonpath='{.items[0].metadata.name}')
  kubectl logs -n $NAMESPACE $CANARY_POD --since=5m | grep -c "ERROR" || echo "0"
  
  # Check memory usage
  echo ""
  echo "💾 Memory Usage:"
  kubectl top pods -n $NAMESPACE -l $CANARY_LABEL
  
  # Check cluster membership
  echo ""
  echo "🔗 Cluster Members:"
  kubectl exec -n $NAMESPACE $CANARY_POD -- \
    curl -s localhost:8558/cluster/members 2>/dev/null | \
    jq -r '.members[] | "\(.node): \(.status)"' || echo "Failed to get members"
  
  sleep 60
done

echo "✅ Monitoring complete!"
```

## Canary Checklist

### Before Deployment

- [ ] Feature flags disabled/enabled appropriately
- [ ] Database migrations completed (if needed)
- [ ] Canary image built and pushed
- [ ] Grafana dashboard ready for comparison
- [ ] Rollback plan prepared
- [ ] Team notified of canary deployment

### During Deployment

- [ ] Deploy canary (1 pod)
- [ ] Verify pod is healthy
- [ ] Verify pod joined cluster
- [ ] Check initial metrics (5 min)
- [ ] No errors in logs
- [ ] Gradual scale up (1 → 2 → 3 pods)
- [ ] Monitor for 15-30 min between increases
- [ ] Compare metrics with stable

### After Deployment

- [ ] All canary pods healthy
- [ ] Error rate comparable to stable
- [ ] Latency comparable to stable
- [ ] No memory leaks observed
- [ ] Cluster stable
- [ ] Update stable deployment
- [ ] Clean up canary resources
- [ ] Document any issues found

### Rollback Procedure

- [ ] Delete canary deployment
- [ ] Verify traffic returns to stable
- [ ] Check stable pods handling increased load
- [ ] Document reason for rollback
- [ ] Create issue for investigation

## Best Practices

### 1. Start Small

Begin with just 1 canary pod (5-10% traffic):
```yaml
replicas:
- name: spring-actor
  count: 1  # Start with 1
```

### 2. Monitor Continuously

Don't just deploy and walk away. Actively monitor:
- Grafana dashboards
- Log streams
- Error rates
- Cluster health

### 3. Use Feature Flags

Combine with feature flags for safer rollouts:
```java
@Value("${feature.new-api.enabled:false}")
private boolean newApiEnabled;

public Response handleRequest() {
    if (newApiEnabled) {
        return newApiHandler();
    } else {
        return legacyHandler();
    }
}
```

### 4. Test in Staging First

Always test canary process in staging:
```bash
# Test in staging
kubectl apply -k overlays/canary --namespace spring-actor-staging
```

### 5. Gradual Rollout

Increase traffic slowly:
- Phase 1: 10% (1 pod)
- Phase 2: 20% (2 pods) - wait 15 min
- Phase 3: 40% (3 pods) - wait 15 min
- Phase 4: 100% (promote to stable)

### 6. Have a Rollback Plan

Know how to rollback quickly:
```bash
# One-command rollback
kubectl delete -k overlays/canary
```

### 7. Document Everything

Keep a canary deployment log:
```
Date: 2024-01-15
Version: v1.1.0
Issue: High latency on /api/search endpoint
Action: Rolled back after 15 minutes
Next Steps: Investigate query performance
```

## Troubleshooting

### Canary Pod Not Starting

```bash
# Check events
kubectl describe pod -n spring-actor -l version=canary

# Check logs
kubectl logs -n spring-actor -l version=canary

# Common issues:
# - Image not found: Check image name/tag
# - OOMKilled: Increase memory limit
# - CrashLoopBackOff: Check application logs
```

### Traffic Not Reaching Canary

```bash
# Check service endpoints
kubectl get endpoints -n spring-actor spring-actor-canary

# Check pod labels
kubectl get pods -n spring-actor -l version=canary --show-labels

# Verify service selector matches pod labels
kubectl get svc -n spring-actor spring-actor-canary -o yaml | grep selector -A 2
```

### High Error Rate in Canary

```bash
# Immediate rollback
kubectl delete -k overlays/canary

# Investigate
kubectl logs -n spring-actor -l version=canary --previous > canary-logs.txt
grep ERROR canary-logs.txt | head -20
```

### Canary Affects Stable

If canary causes issues in stable deployment:

```bash
# Isolate canary (remove from cluster)
kubectl patch deployment spring-actor-canary -n spring-actor \
  --type=json -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/env/-", "value":{"name":"PEKKO_CLUSTER_SEED_NODES","value":"spring-actor-canary-0.spring-actor"}}]'

# Or delete immediately
kubectl delete deployment spring-actor-canary -n spring-actor
```

## Advanced: Automated Canary with CI/CD

```yaml
# .github/workflows/canary-deploy.yml
name: Canary Deployment

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: 'Image tag for canary'
        required: true

jobs:
  deploy-canary:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Deploy Canary
      run: |
        cd example/kubernetes/overlays/canary
        kustomize edit set image your-registry/spring-actor-app=your-registry.io/spring-actor-app:${{ github.event.inputs.image_tag }}
        kubectl apply -k .
    
    - name: Wait and Monitor
      run: |
        sleep 300  # Wait 5 minutes
        
        # Check error rate
        ERROR_RATE=$(./scripts/check-error-rate.sh canary)
        if [ "$ERROR_RATE" -gt "1" ]; then
          echo "Error rate too high: $ERROR_RATE%"
          kubectl delete -k example/kubernetes/overlays/canary
          exit 1
        fi
    
    - name: Promote to Production
      if: success()
      run: |
        cd example/kubernetes/overlays/prod
        kustomize edit set image your-registry/spring-actor-app=your-registry.io/spring-actor-app:${{ github.event.inputs.image_tag }}
        kubectl apply -k .
        
        # Cleanup canary
        kubectl delete -k example/kubernetes/overlays/canary
```

---

For more information, see:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Operations Runbook](OPERATIONS_RUNBOOK.md)
- [CI/CD Guide](CICD_GUIDE.md)
