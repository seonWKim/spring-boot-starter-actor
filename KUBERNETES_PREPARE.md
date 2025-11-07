# Kubernetes Deployment Guide for Spring Boot Starter Actor

This guide provides comprehensive instructions for deploying Spring Boot Starter Actor applications with Pekko clustering to Kubernetes, with support for rolling updates.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Architecture Changes](#architecture-changes)
4. [Dependencies](#dependencies)
5. [Application Configuration](#application-configuration)
6. [Kubernetes Resources](#kubernetes-resources)
7. [Rolling Updates Deep Dive](#rolling-updates-deep-dive)
8. [Deployment Process](#deployment-process)
9. [Monitoring and Health Checks](#monitoring-and-health-checks)
10. [Troubleshooting](#troubleshooting)
11. [Best Practices](#best-practices)

---

## Overview

### Current Setup vs Kubernetes Setup

**Current Setup (Static Seed Nodes):**
- Uses hardcoded seed node addresses in `application.yml`
- Requires knowing all node addresses upfront
- Not suitable for dynamic environments like Kubernetes

**Kubernetes Setup (Dynamic Cluster Bootstrap):**
- Uses **Pekko Cluster Bootstrap** for automatic cluster formation
- Discovers peer nodes via **Kubernetes API**
- Handles dynamic pod creation/deletion during rolling updates
- Supports zero-downtime deployments

### Key Concepts

1. **Cluster Bootstrap**: Mechanism for nodes to automatically discover and form/join clusters
2. **Service Discovery**: Kubernetes API-based discovery of peer pods
3. **Management HTTP Endpoint**: Each pod exposes HTTP endpoints for cluster management
4. **Split Brain Resolver**: Handles network partitions automatically
5. **Coordinated Shutdown**: Ensures graceful shutdown during rolling updates

---

## Prerequisites

- Kubernetes cluster (1.19+)
- `kubectl` configured
- Docker registry access
- Understanding of Kubernetes Deployments, Services, and RBAC

---

## Architecture Changes

### From Static Seed Nodes to Cluster Bootstrap

#### Before (Static - Current Setup):

```yaml
spring:
  actor:
    pekko:
      cluster:
        seed-nodes: pekko://spring-pekko-example@chat-app-0:2551,pekko://spring-pekko-example@chat-app-1:2552
```

**Problems:**
- Pod names/IPs change during rolling updates
- Can't scale dynamically
- Requires manual configuration changes

#### After (Dynamic - Kubernetes):

```yaml
spring:
  actor:
    pekko:
      management:
        http:
          hostname: ${POD_IP}
          port: 8558
          bind-hostname: 0.0.0.0
          bind-port: 8558
      discovery:
        method: kubernetes-api
        kubernetes-api:
          pod-namespace: ${NAMESPACE}
          pod-label-selector: app=spring-actor-app
      cluster:
        bootstrap:
          contact-point-discovery:
            discovery-method: kubernetes-api
            service-name: spring-actor-app
            required-contact-point-nr: 2
```

**Benefits:**
- Automatic peer discovery
- Supports rolling updates
- Dynamic scaling
- No hardcoded addresses

---

## Dependencies

### 1. Add Pekko Management Dependencies

Add to your `build.gradle`:

```gradle
dependencies {
    // Existing Pekko dependencies
    implementation 'org.apache.pekko:pekko-actor-typed_3:1.1.4'
    implementation 'org.apache.pekko:pekko-cluster-typed_3:1.1.4'
    implementation 'org.apache.pekko:pekko-cluster-sharding-typed_3:1.1.4'

    // NEW: Add these for Kubernetes deployment
    implementation 'org.apache.pekko:pekko-management_3:1.0.5'
    implementation 'org.apache.pekko:pekko-management-cluster-bootstrap_3:1.0.5'
    implementation 'org.apache.pekko:pekko-management-cluster-http_3:1.0.5'
    implementation 'org.apache.pekko:pekko-discovery-kubernetes-api_3:1.0.5'
}
```

Or for Maven:

```xml
<dependencies>
    <!-- Existing Pekko dependencies -->

    <!-- NEW: Add these for Kubernetes deployment -->
    <dependency>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-management_3</artifactId>
        <version>1.0.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-management-cluster-bootstrap_3</artifactId>
        <version>1.0.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-management-cluster-http_3</artifactId>
        <version>1.0.5</version>
    </dependency>
    <dependency>
        <groupId>org.apache.pekko</groupId>
        <artifactId>pekko-discovery-kubernetes-api_3</artifactId>
        <version>1.0.5</version>
    </dependency>
</dependencies>
```

### 2. Update Application Configuration

Update `application.yml` to support both local and Kubernetes deployments:

```yaml
spring:
  application:
    name: spring-actor-app
  actor:
    pekko:
      name: spring-actor-system
      actor:
        provider: cluster
        allow-java-serialization: off
        warn-about-java-serializer-usage: on

      # Remote communication configuration
      remote:
        artery:
          canonical:
            # In Kubernetes, use POD_IP
            hostname: ${PEKKO_HOSTNAME:127.0.0.1}
            port: ${PEKKO_PORT:2551}
          bind:
            hostname: 0.0.0.0
            port: 2551

      # Cluster configuration
      cluster:
        # Remove static seed-nodes for Kubernetes!
        # seed-nodes: [] # Don't use this in Kubernetes

        # Split Brain Resolver (CRITICAL for production)
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          active-strategy: keep-majority
          stable-after: 20s

        # Graceful shutdown
        shutdown-after-unsuccessful-join-seed-nodes: 60s
        coordinated-shutdown:
          phases:
            cluster-leave:
              timeout: 10s
            actor-system-terminate:
              timeout: 10s

      # NEW: Management HTTP endpoints
      management:
        http:
          hostname: ${POD_IP:127.0.0.1}
          port: 8558
          bind-hostname: 0.0.0.0
          bind-port: 8558

      # NEW: Discovery configuration
      discovery:
        method: ${DISCOVERY_METHOD:config}
        kubernetes-api:
          pod-namespace: ${NAMESPACE:default}
          pod-label-selector: app=${LABEL_SELECTOR:spring-actor-app}
          pod-port-name: management

      # NEW: Cluster Bootstrap
      cluster:
        bootstrap:
          contact-point-discovery:
            discovery-method: ${DISCOVERY_METHOD:config}
            service-name: ${SERVICE_NAME:spring-actor-app}
            required-contact-point-nr: ${REQUIRED_CONTACT_POINTS:2}
            stable-margin: 5s
            interval: 1s
            exponential-backoff-random-factor: 0.1
            exponential-backoff-max: 15s
            contact-with-all-contact-points: true

server:
  port: ${SERVER_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  health:
    readinessState:
      enabled: true
    livenessState:
      enabled: true
```

### 3. Enable Management in Spring Boot

Add a configuration bean to start Pekko Management:

```java
package io.github.seonwkim.config;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.javadsl.PekkoManagement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("kubernetes") // Only activate in Kubernetes
public class KubernetesClusterConfig {

    @Bean
    public PekkoManagementStarter pekkoManagementStarter(
            io.github.seonwkim.core.SpringActorSystem springActorSystem) {
        ActorSystem<?> actorSystem = springActorSystem.getRaw();

        // Start Pekko Management HTTP server
        PekkoManagement management = PekkoManagement.get(actorSystem);
        management.start();

        // Start Cluster Bootstrap
        ClusterBootstrap bootstrap = ClusterBootstrap.get(actorSystem);
        bootstrap.start();

        return new PekkoManagementStarter(management, bootstrap);
    }

    public static class PekkoManagementStarter {
        private final PekkoManagement management;
        private final ClusterBootstrap bootstrap;

        public PekkoManagementStarter(PekkoManagement management, ClusterBootstrap bootstrap) {
            this.management = management;
            this.bootstrap = bootstrap;
        }
    }
}
```

---

## Kubernetes Resources

### 1. Namespace

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: spring-actor
```

### 2. RBAC Configuration

Pekko needs permission to list pods via Kubernetes API.

```yaml
# rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-actor-sa
  namespace: spring-actor

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: spring-actor-pod-reader
  namespace: spring-actor
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "watch", "list"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: spring-actor-pod-reader-binding
  namespace: spring-actor
subjects:
- kind: ServiceAccount
  name: spring-actor-sa
  namespace: spring-actor
roleRef:
  kind: Role
  name: spring-actor-pod-reader
  apiGroup: rbac.authorization.k8s.io
```

### 3. Headless Service (for Discovery)

```yaml
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-actor-app
  namespace: spring-actor
  labels:
    app: spring-actor-app
spec:
  clusterIP: None  # Headless service for peer discovery
  ports:
  - name: management
    port: 8558
    targetPort: 8558
    protocol: TCP
  - name: remoting
    port: 2551
    targetPort: 2551
    protocol: TCP
  selector:
    app: spring-actor-app

---
# Regular service for HTTP traffic
apiVersion: v1
kind: Service
metadata:
  name: spring-actor-app-http
  namespace: spring-actor
  labels:
    app: spring-actor-app
spec:
  type: LoadBalancer  # or NodePort/ClusterIP
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  selector:
    app: spring-actor-app
```

### 4. Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor-app
  namespace: spring-actor
  labels:
    app: spring-actor-app
spec:
  replicas: 3

  # CRITICAL: Rolling update strategy
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0    # Never take down existing pods until new ones are ready
      maxSurge: 1          # Only create 1 new pod at a time

  selector:
    matchLabels:
      app: spring-actor-app

  template:
    metadata:
      labels:
        app: spring-actor-app
    spec:
      serviceAccountName: spring-actor-sa

      # Anti-affinity to spread pods across nodes
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - spring-actor-app
              topologyKey: kubernetes.io/hostname

      containers:
      - name: spring-actor-app
        image: your-registry/spring-actor-app:latest
        imagePullPolicy: Always

        # Environment variables
        env:
        - name: NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: PEKKO_HOSTNAME
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        - name: PEKKO_PORT
          value: "2551"
        - name: SERVER_PORT
          value: "8080"
        - name: DISCOVERY_METHOD
          value: "kubernetes-api"
        - name: SERVICE_NAME
          value: "spring-actor-app"
        - name: LABEL_SELECTOR
          value: "spring-actor-app"
        - name: REQUIRED_CONTACT_POINTS
          value: "2"
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: JAVA_OPTS
          value: >-
            -XX:+UseContainerSupport
            -XX:MaxRAMPercentage=75
            -XX:+UseG1GC
            -XX:+UseStringDeduplication
            -XX:+HeapDumpOnOutOfMemoryError
            -XX:HeapDumpPath=/tmp/heapdump.hprof

        # Ports
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        - name: remoting
          containerPort: 2551
          protocol: TCP
        - name: management
          containerPort: 8558
          protocol: TCP

        # Liveness probe - checks if app is alive
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3

        # Readiness probe - checks if app can receive traffic
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

        # Resources
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"  # Use requests, not limits!
          limits:
            memory: "2Gi"
            # DO NOT set cpu limits! This can cause performance issues

        # Graceful shutdown
        lifecycle:
          preStop:
            exec:
              command: ["/bin/sh", "-c", "sleep 10"]
```

### 5. Dockerfile Update

Update your Dockerfile to support Kubernetes:

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy application jar
COPY build/libs/*.jar app.jar

# Create heap dump directory
RUN mkdir -p /tmp && chmod 777 /tmp

# Set non-root user
RUN addgroup --system --gid 1001 appuser && \
    adduser --system --uid 1001 --gid 1001 appuser && \
    chown -R appuser:appuser /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Rolling Updates Deep Dive

### How Rolling Updates Work in Pekko Clusters

Rolling updates are **critical** for zero-downtime deployments. Here's what happens step-by-step:

#### Phase 1: Before Update

```
Cluster State: STABLE
┌─────────────────────────────────────────┐
│  Pod A (v1)  │  Pod B (v1)  │  Pod C (v1)│
│  ●───────────●───────────────●          │
│  All pods are members of the cluster    │
└─────────────────────────────────────────┘
```

#### Phase 2: New Pod Created (maxSurge: 1)

```
Cluster State: JOINING
┌──────────────────────────────────────────────┐
│  Pod A (v1)  │  Pod B (v1)  │  Pod C (v1)   │
│  ●───────────●───────────────●               │
│                               ↓               │
│                          Pod D (v2) [NEW]    │
│                               ◐               │
└──────────────────────────────────────────────┘

1. Kubernetes creates Pod D with v2 image
2. Pod D starts and queries Kubernetes API for peers
3. Pod D finds Pods A, B, C via label selector
4. Pod D contacts Pods A, B, C on management port (8558)
5. Pod D joins the existing cluster
6. Cluster size: 4 nodes (temporarily)
```

**Important:**
- Pod D uses the **same cluster name** (spring-actor-system)
- Pod D discovers peers via **Kubernetes API**, not static seed nodes
- Old and new versions coexist temporarily
- Split Brain Resolver handles this gracefully

#### Phase 3: Old Pod Terminated

```
Cluster State: LEAVING
┌──────────────────────────────────────────────┐
│  [TERMINATING]  │  Pod B (v1)  │  Pod C (v1) │
│  Pod A (v1) ○   │  ●           │  ●          │
│      ↑          │  │           │  │          │
│   PreStop       │  └───────────┴─┘           │
│   (sleep 10s)   │                            │
│                 │  Pod D (v2) ●──────────┐   │
└──────────────────────────────────────────────┘

1. Kubernetes sends SIGTERM to Pod A
2. PreStop hook executes (sleep 10s)
3. Pod A receives shutdown signal
4. Coordinated Shutdown begins:
   - Pod A stops accepting new messages
   - Cluster Singleton migrates to another node
   - Sharded entities rebalance to other nodes
   - Pod A leaves cluster gracefully
5. After graceful shutdown, Pod A terminates
6. Cluster size: 3 nodes again
```

#### Phase 4: Repeat Until Complete

```
Final State: STABLE (all v2)
┌─────────────────────────────────────────┐
│  Pod D (v2)  │  Pod E (v2)  │  Pod F (v2)│
│  ●───────────●───────────────●          │
│  All pods running v2                    │
└─────────────────────────────────────────┘
```

### How Old and New Clusters Are Distinguished

**They are NOT separate clusters!** This is crucial to understand.

#### Same Cluster, Different Versions

```
Cluster Name: spring-actor-system (same for all versions)
Service Name: spring-actor-app (same for all versions)

During rolling update:
┌────────────────────────────────────────────────┐
│  SINGLE CLUSTER: "spring-actor-system"         │
│                                                 │
│  Members:                                       │
│  ├─ Pod A (v1.0) - Member                      │
│  ├─ Pod B (v1.0) - Member                      │
│  ├─ Pod C (v1.0) - Member                      │
│  └─ Pod D (v2.0) - Joining → Member            │
│                                                 │
│  All nodes see each other as cluster members   │
└────────────────────────────────────────────────┘
```

#### Discovery Process

1. **Pod D (v2) starts and queries Kubernetes API:**
   ```
   GET /api/v1/namespaces/spring-actor/pods?labelSelector=app=spring-actor-app
   ```

2. **Kubernetes returns ALL pods with that label:**
   ```json
   {
     "items": [
       {"name": "pod-a", "ip": "10.1.1.1", "version": "v1"},
       {"name": "pod-b", "ip": "10.1.1.2", "version": "v1"},
       {"name": "pod-c", "ip": "10.1.1.3", "version": "v1"},
       {"name": "pod-d", "ip": "10.1.1.4", "version": "v2"}
     ]
   }
   ```

3. **Pod D contacts ALL discovered pods:**
   ```
   HTTP GET http://10.1.1.1:8558/bootstrap/seed-nodes
   HTTP GET http://10.1.1.2:8558/bootstrap/seed-nodes
   HTTP GET http://10.1.1.3:8558/bootstrap/seed-nodes
   ```

4. **Pods respond with cluster membership info:**
   - If cluster exists → Pod D joins existing cluster
   - If no cluster exists → Pod with lowest address forms cluster

#### Version Compatibility

**Important Considerations:**

1. **Binary Compatibility**:
   - Pekko uses Java serialization or custom serializers
   - Ensure message classes are binary compatible between versions
   - Use `@SerialVersionUID` on message classes

2. **Rolling Update Requirements**:
   - Keep message formats backward compatible
   - Don't remove/rename message classes during rolling update
   - Test compatibility between versions before deploying

3. **Cluster Bootstrap Configuration**:
   - `required-contact-point-nr: 2` means at least 2 pods must be discovered
   - This prevents split-brain during rolling updates
   - Set this to `replicas / 2` (e.g., 3 replicas → 2 required)

### Rolling Update Timing

```
Timeline of a Rolling Update (3 replicas):

T+0s:   Deployment updated with new image
        ├─ Cluster: [A(v1), B(v1), C(v1)] - Size: 3

T+5s:   New pod D(v2) created
        ├─ Cluster: [A(v1), B(v1), C(v1), D(v2)] - Size: 4
        └─ D(v2) joining...

T+20s:  D(v2) ready (passes readiness probe)
        ├─ Cluster: [A(v1), B(v1), C(v1), D(v2)] - Size: 4
        └─ All nodes healthy

T+25s:  A(v1) receives SIGTERM
        ├─ PreStop: sleep 10s
        ├─ Coordinated shutdown begins
        └─ A(v1) leaves cluster

T+35s:  A(v1) terminated
        ├─ Cluster: [B(v1), C(v1), D(v2)] - Size: 3

T+40s:  New pod E(v2) created
        ├─ Cluster: [B(v1), C(v1), D(v2), E(v2)] - Size: 4
        └─ E(v2) joining...

T+55s:  E(v2) ready
        ├─ Cluster: [B(v1), C(v1), D(v2), E(v2)] - Size: 4

T+60s:  B(v1) terminated
        ├─ Cluster: [C(v1), D(v2), E(v2)] - Size: 3

... (repeat for C)

T+100s: Rolling update complete
        └─ Cluster: [D(v2), E(v2), F(v2)] - Size: 3
```

### Critical Configuration for Rolling Updates

#### 1. Coordinated Shutdown

```yaml
spring:
  actor:
    pekko:
      cluster:
        coordinated-shutdown:
          phases:
            # Phase 1: Leave cluster
            cluster-leave:
              timeout: 10s
              depends-on: [service-unbind]

            # Phase 2: Stop sharding
            cluster-sharding-shutdown-region:
              timeout: 10s
              depends-on: [cluster-leave]

            # Phase 3: Terminate actor system
            actor-system-terminate:
              timeout: 10s
              depends-on: [cluster-sharding-shutdown-region]
```

#### 2. Split Brain Resolver

```yaml
spring:
  actor:
    pekko:
      cluster:
        downing-provider-class: org.apache.pekko.cluster.sbr.SplitBrainResolverProvider
        split-brain-resolver:
          active-strategy: keep-majority
          stable-after: 20s
          down-all-when-unstable: on
```

**Strategies:**
- `keep-majority`: Keep the partition with majority of nodes
- `static-quorum`: Keep partition with at least N nodes
- `keep-oldest`: Keep partition with oldest node
- `down-all`: Down all nodes (restart required)

#### 3. PreStop Hook

```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 10"]
```

**Why 10 seconds?**
- Gives time for:
  - Readiness probe to fail
  - Load balancer to stop routing traffic
  - In-flight requests to complete
  - Cluster to start coordinated shutdown

---

## Deployment Process

### 1. Build and Push Docker Image

```bash
# Build application
./gradlew clean build

# Build Docker image
docker build -t your-registry/spring-actor-app:1.0.0 .

# Push to registry
docker push your-registry/spring-actor-app:1.0.0
```

### 2. Apply Kubernetes Resources

```bash
# Create namespace and RBAC
kubectl apply -f namespace.yaml
kubectl apply -f rbac.yaml

# Create services
kubectl apply -f service.yaml

# Deploy application
kubectl apply -f deployment.yaml
```

### 3. Verify Cluster Formation

```bash
# Watch pods starting
kubectl get pods -n spring-actor -w

# Check logs of first pod
kubectl logs -n spring-actor spring-actor-app-xxx -f

# Look for these log lines:
# - "Cluster Node [pekko://spring-actor-system@10.1.1.1:2551] - Node [pekko://...] is JOINING"
# - "Cluster Node [pekko://spring-actor-system@10.1.1.1:2551] - Leader is moving node [pekko://...] to [Up]"
```

### 4. Perform Rolling Update

```bash
# Update image in deployment
kubectl set image deployment/spring-actor-app \
  spring-actor-app=your-registry/spring-actor-app:1.0.1 \
  -n spring-actor

# Watch rolling update
kubectl rollout status deployment/spring-actor-app -n spring-actor

# Check cluster state during update
kubectl exec -n spring-actor spring-actor-app-xxx -- \
  curl localhost:8558/cluster/members
```

### 5. Monitor Update

```bash
# Check cluster membership
kubectl exec -n spring-actor <pod-name> -- \
  curl -s localhost:8558/cluster/members | jq

# Expected output during rolling update:
{
  "selfNode": "pekko://spring-actor-system@10.1.1.4:2551",
  "members": [
    {
      "node": "pekko://spring-actor-system@10.1.1.1:2551",
      "nodeUid": "123",
      "status": "Leaving",
      "roles": []
    },
    {
      "node": "pekko://spring-actor-system@10.1.1.2:2551",
      "nodeUid": "456",
      "status": "Up",
      "roles": []
    },
    {
      "node": "pekko://spring-actor-system@10.1.1.3:2551",
      "nodeUid": "789",
      "status": "Up",
      "roles": []
    },
    {
      "node": "pekko://spring-actor-system@10.1.1.4:2551",
      "nodeUid": "101",
      "status": "Joining",
      "roles": []
    }
  ],
  "unreachable": [],
  "leader": "pekko://spring-actor-system@10.1.1.2:2551",
  "oldest": "pekko://spring-actor-system@10.1.1.2:2551"
}
```

---

## Monitoring and Health Checks

### 1. Health Check Endpoints

**Liveness Probe** (Is the app alive?):
```bash
curl http://localhost:8080/actuator/health/liveness
```

**Readiness Probe** (Can the app receive traffic?):
```bash
curl http://localhost:8080/actuator/health/readiness
```

**Cluster Health** (Is cluster healthy?):
```bash
curl http://localhost:8558/cluster/members
curl http://localhost:8558/ready
```

### 2. Prometheus Metrics

Add to your `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: spring-actor-app
      environment: production
```

**Key Metrics:**
- `pekko_cluster_members`: Number of cluster members
- `pekko_cluster_unreachable`: Number of unreachable nodes
- `actor_message_processing_time`: Message processing latency
- `actor_mailbox_size`: Mailbox queue depth

### 3. Logging

Configure structured logging for better observability:

```yaml
logging:
  level:
    org.apache.pekko: INFO
    org.apache.pekko.cluster: INFO
    org.apache.pekko.management: DEBUG
    io.github.seonwkim: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

## Troubleshooting

### Issue 1: Pods Not Forming Cluster

**Symptoms:**
- Logs show "No seed-nodes configured, manual cluster join required"
- Pods stay in "Joining" state

**Diagnosis:**
```bash
# Check RBAC permissions
kubectl auth can-i list pods --as=system:serviceaccount:spring-actor:spring-actor-sa -n spring-actor

# Check service discovery
kubectl exec -n spring-actor <pod-name> -- \
  curl localhost:8558/bootstrap/seed-nodes
```

**Solutions:**
1. Verify RBAC is configured correctly
2. Check that pod label selector matches deployment labels
3. Ensure `required-contact-point-nr` is not too high
4. Check that management port (8558) is accessible

### Issue 2: Split Brain During Rolling Update

**Symptoms:**
- Multiple cluster leaders
- Unreachable members
- Data inconsistencies

**Diagnosis:**
```bash
# Check for unreachable members
kubectl exec -n spring-actor <pod-name> -- \
  curl localhost:8558/cluster/members | jq '.unreachable'
```

**Solutions:**
1. Ensure Split Brain Resolver is configured
2. Slow down rolling update: `maxSurge: 1, maxUnavailable: 0`
3. Increase `stable-after` duration in SBR config
4. Check network policies aren't blocking cluster communication

### Issue 3: Slow Rolling Updates

**Symptoms:**
- Pods take minutes to become ready
- Timeouts during deployment

**Diagnosis:**
```bash
# Check readiness probe
kubectl describe pod -n spring-actor <pod-name>

# Check bootstrap logs
kubectl logs -n spring-actor <pod-name> | grep -i bootstrap
```

**Solutions:**
1. Increase readiness probe `initialDelaySeconds`
2. Decrease `contact-point-discovery.interval` for faster discovery
3. Reduce `required-contact-point-nr` for small clusters
4. Check that DNS resolution is working

### Issue 4: Memory Issues

**Symptoms:**
- OOMKilled pods
- High heap usage

**Diagnosis:**
```bash
# Check memory usage
kubectl top pod -n spring-actor

# Get heap dump
kubectl exec -n spring-actor <pod-name> -- \
  jcmd 1 GC.heap_dump /tmp/heapdump.hprof
```

**Solutions:**
1. Increase memory limits
2. Tune JVM: `-XX:MaxRAMPercentage=75`
3. Use G1GC: `-XX:+UseG1GC`
4. Monitor actor mailbox sizes

### Issue 5: Cluster Not Discovering New Pods

**Symptoms:**
- New pods can't join cluster
- Discovery returns empty list

**Diagnosis:**
```bash
# Test Kubernetes API access from pod
kubectl exec -n spring-actor <pod-name> -- \
  curl -H "Authorization: Bearer $(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
  https://kubernetes.default.svc/api/v1/namespaces/spring-actor/pods
```

**Solutions:**
1. Check ServiceAccount is bound to pod
2. Verify RBAC Role has `list` permission on pods
3. Ensure pod has `app` label
4. Check that `pod-label-selector` matches

---

## Best Practices

### 1. Cluster Sizing

**Minimum:**
- Production: 3 nodes minimum
- Development: 1 node is OK

**Scaling:**
- Scale in increments of 1-2 nodes
- Don't scale up/down too quickly
- Use HPA carefully (cluster formation takes time)

### 2. Resource Configuration

**CPU:**
- ✅ **DO** use `requests.cpu`
- ❌ **DON'T** use `limits.cpu` (can cause severe performance issues)
- Reason: CPU throttling breaks Pekko's timing assumptions

**Memory:**
- ✅ **DO** use both `requests` and `limits`
- Set `limits` to 1.5-2x `requests`
- Use `-XX:MaxRAMPercentage=75` to leave headroom

**Example:**
```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    # No CPU limit!
```

### 3. Deployment Strategy

**For Small Clusters (3-5 nodes):**
```yaml
strategy:
  rollingUpdate:
    maxUnavailable: 0
    maxSurge: 1
```

**For Large Clusters (10+ nodes):**
```yaml
strategy:
  rollingUpdate:
    maxUnavailable: 1
    maxSurge: 2
```

### 4. Health Checks

**Liveness Probe:**
- Check if JVM is alive
- Don't check cluster membership (too strict)
- Long initial delay: 30-60 seconds

**Readiness Probe:**
- Check if app can receive traffic
- Can check cluster membership
- Shorter initial delay: 20 seconds

### 5. Graceful Shutdown

**PreStop Hook:**
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 10"]
```

**Termination Grace Period:**
```yaml
terminationGracePeriodSeconds: 30
```

### 6. Version Compatibility

**Safe Rolling Updates:**
1. Ensure message serialization is compatible
2. Test rolling update in staging first
3. Don't change cluster configuration during update
4. Keep old version running until new is stable

**Unsafe Changes:**
- Changing actor system name
- Changing cluster roles
- Incompatible message serialization
- Major Pekko version upgrades

### 7. Monitoring

**Must-Monitor Metrics:**
- Cluster size
- Unreachable nodes
- Mailbox sizes
- Message processing time
- JVM heap usage
- Pod restart count

**Alerting:**
- Alert on unreachable nodes > 0
- Alert on cluster size < min replicas
- Alert on high mailbox size (> 1000)
- Alert on OOMKilled pods

### 8. Backup and Recovery

**State:**
- Use external storage for persistent state (database, S3)
- Don't rely on pod filesystem
- Actors should be able to rebuild state from external source

**Configuration:**
- Store config in ConfigMaps/Secrets
- Version config with application version
- Use GitOps for config management

### 9. Security

**Network Policies:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: spring-actor-netpol
  namespace: spring-actor
spec:
  podSelector:
    matchLabels:
      app: spring-actor-app
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: spring-actor-app
    ports:
    - protocol: TCP
      port: 2551  # Remoting
    - protocol: TCP
      port: 8558  # Management
  - from:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 8080  # HTTP
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: spring-actor-app
    ports:
    - protocol: TCP
      port: 2551
    - protocol: TCP
      port: 8558
  - to:
    - namespaceSelector:
        matchLabels:
          name: kube-system
    ports:
    - protocol: TCP
      port: 53  # DNS
```

**TLS:**
- Enable TLS for remoting in production
- Use cert-manager for certificate management
- Configure TLS in `pekko.remote.artery.ssl`

### 10. Testing Rolling Updates

**Before Production:**

1. **Test in Staging:**
   ```bash
   # Perform rolling update
   kubectl set image deployment/spring-actor-app \
     spring-actor-app=your-registry/spring-actor-app:new-version \
     -n staging

   # Monitor cluster state
   watch -n 1 'kubectl exec -n staging <pod> -- \
     curl -s localhost:8558/cluster/members | jq .members[].status'
   ```

2. **Chaos Testing:**
   - Use tools like Chaos Mesh or Litmus
   - Test pod failures during rolling update
   - Verify cluster recovers gracefully

3. **Load Testing:**
   - Run load tests during rolling update
   - Verify no dropped messages
   - Check latency doesn't spike

---

## Summary Checklist

Before deploying to Kubernetes:

- [ ] Add Pekko Management dependencies
- [ ] Configure Cluster Bootstrap in `application.yml`
- [ ] Add `KubernetesClusterConfig` bean
- [ ] Create Kubernetes namespace
- [ ] Configure RBAC (ServiceAccount, Role, RoleBinding)
- [ ] Create headless Service for discovery
- [ ] Create Deployment with rolling update strategy
- [ ] Configure health checks (liveness, readiness)
- [ ] Set resource requests (CPU, memory)
- [ ] Configure graceful shutdown (preStop, termination grace period)
- [ ] Enable Split Brain Resolver
- [ ] Set up monitoring (Prometheus, logs)
- [ ] Test rolling update in staging
- [ ] Document rollback procedure
- [ ] Set up alerts for cluster health

---

## References

- [Pekko Deployment Documentation](https://pekko.apache.org/docs/pekko/current/additional/deploying.html)
- [Pekko Kubernetes Deployment Guide](https://pekko.apache.org/docs/pekko-management/current/kubernetes-deployment/index.html)
- [Pekko Cluster Bootstrap](https://pekko.apache.org/docs/pekko-management/current/bootstrap/)
- [Pekko Cluster](https://pekko.apache.org/docs/pekko/current/typed/cluster.html)
- [Kubernetes Service Discovery](https://pekko.apache.org/docs/pekko-management/current/discovery/kubernetes.html)

---

## Next Steps

1. **Implement**: Follow this guide to deploy to Kubernetes
2. **Test**: Perform rolling updates in staging environment
3. **Monitor**: Set up comprehensive monitoring and alerting
4. **Document**: Document your specific deployment procedures
5. **Automate**: Create CI/CD pipelines for automated deployments
6. **Scale**: Test scaling up/down to verify cluster stability
7. **Disaster Recovery**: Test failure scenarios and recovery procedures
