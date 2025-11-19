# Task 4.2: Kubernetes Readiness and Liveness Probes

**Priority:** HIGH
**Estimated Effort:** 1-2 days
**Dependencies:** Task 4.1
**Assignee:** AI Agent

---

## Objective

Configure Kubernetes probes for production-ready actor system deployments.

---

## Implementation

### 1. Readiness Probe Configuration

**Purpose:** Determine if pod can receive traffic

**Health Groups:**
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: actorSystem, cluster, db
          show-details: always
```

**Kubernetes Configuration:**
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3
```

### 2. Liveness Probe Configuration

**Purpose:** Determine if pod needs restart

**Health Groups:**
```yaml
management:
  endpoint:
    health:
      group:
        liveness:
          include: actorSystem
          show-details: always
```

**Kubernetes Configuration:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 20
  timeoutSeconds: 5
  failureThreshold: 5
```

### 3. Startup Probe Configuration (for slow-starting apps)

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 30
```

---

## Files to Create/Update

1. **`example/chat/src/main/resources/application.yml`**
   - Add health probe configuration
   - Configure readiness/liveness groups

2. **`example/chat/k8s/deployment.yaml`**
   - Add complete probe configuration

3. **`docs/kubernetes-probes.md`**
   - Document probe configuration
   - Best practices guide
   - Troubleshooting tips

---

## Readiness vs Liveness Guidelines

### Readiness Probe Should Check:
- Actor system initialized
- Cluster joined (if clustering enabled)
- Database connections available
- Essential actors started

### Liveness Probe Should Check:
- Actor system responsive
- No deadlocks
- Message processing working
- Health check endpoint accessible

**Important:** Liveness probe should be more lenient than readiness to avoid restart loops.

---

## Configuration

```yaml
spring:
  actor:
    health:
      readiness:
        enabled: true
        checks:
          - actorSystem
          - cluster
          - essentialActors
      liveness:
        enabled: true
        checks:
          - actorSystem
        timeout: 5s
```

---

## Testing

### Manual Testing:
```bash
# Test readiness
curl http://localhost:8080/actuator/health/readiness

# Test liveness
curl http://localhost:8080/actuator/health/liveness

# Test full health
curl http://localhost:8080/actuator/health
```

### Integration Tests:
- Simulate actor system initialization
- Simulate cluster join
- Verify probe responses

---

## Acceptance Criteria

- [ ] Readiness probe configuration documented
- [ ] Liveness probe configuration documented
- [ ] Health groups properly configured
- [ ] Example Kubernetes manifests provided
- [ ] Probe thresholds tuned appropriately
- [ ] Documentation includes troubleshooting guide
- [ ] Tested in Kubernetes environment
- [ ] No false positives causing unnecessary restarts

---

## Example Deployment

**Complete example:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: actor-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: actor-app:latest
        ports:
        - containerPort: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 20
```

---

## Notes

- Tune initialDelaySeconds based on actual startup time
- Set failureThreshold to avoid flapping
- Monitor probe failures in production
- Consider startup probe for apps taking > 60s to start
