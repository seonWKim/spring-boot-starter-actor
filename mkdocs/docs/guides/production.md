# Running in Production

This guide covers settings that matter when you run actor applications in production, for example on Kubernetes.

## Graceful shutdown

When the Spring application context closes (on `SIGTERM`, or when you call `context.close()`), the starter shuts the actor system down through Pekko **coordinated shutdown**. In cluster mode this means the node leaves the cluster on purpose: other nodes see it go `Leaving` → `Exiting` → `Removed`, sharded entities and cluster singletons move to the remaining nodes, and the node is never reported as unreachable or downed by the split brain resolver.

### Shutdown order

The starter registers a Spring `SmartLifecycle` bean, `ActorSystemShutdownLifecycle`, at phase `ActorSystemShutdownLifecycle.PHASE` (`Integer.MAX_VALUE - 4096`). Spring stops lifecycle beans from the highest phase to the lowest before it destroys any bean, so on shutdown:

1. With `server.shutdown=graceful`, the web server stops accepting requests and drains in-flight ones (Spring Boot's web server lifecycles run at higher phases).
2. The actor system runs coordinated shutdown and the starter waits for it to terminate, at most the configured timeout.
3. Spring destroys the remaining beans, including the services, repositories and connection pools your actors use. Actors are already stopped, so they never call a destroyed bean.

The wait is bounded: if an actor blocks shutdown (for example a handler stuck on blocking I/O), closing the context returns after the timeout with a warning in the log, and the process can exit instead of hanging.

The shutdown runs once. If the actor system has already terminated, for example because the node was downed and Pekko shut itself down, closing the context does nothing more and does not fail.

### Shutdown timeout

```yaml
spring:
  actor:
    shutdown:
      timeout: 20s   # default
```

`spring.actor.shutdown.timeout` is how long closing the context waits for the actor system to terminate. It takes any Spring Boot duration (`15s`, `500ms`, `PT20S`). The default is `20s`.

Keep Pekko's own coordinated shutdown phase timeouts (`pekko.coordinated-shutdown.phases.<phase>.timeout`) shorter than this setting, so the phases that matter for a graceful leave (`cluster-leave`, `cluster-sharding-shutdown-region`, `cluster-exiting`) finish inside the wait.

### Kubernetes: `terminationGracePeriodSeconds` and the `preStop` hook

When Kubernetes stops a pod it runs the `preStop` hook, then sends `SIGTERM`, and sends `SIGKILL` once `terminationGracePeriodSeconds` has passed since the start, whatever is still running. A pod killed with `SIGKILL` cannot leave the cluster and is seen as unreachable by the other nodes. So the grace period must cover all three steps:

```
terminationGracePeriodSeconds >= preStop sleep
                                 + web server drain (spring.lifecycle.timeout-per-shutdown-phase)
                                 + spring.actor.shutdown.timeout
                                 + a few seconds margin
```

A `preStop` sleep gives the readiness probe time to fail and the load balancer time to stop sending traffic before the application begins to shut down:

```yaml
spec:
  terminationGracePeriodSeconds: 45
  containers:
    - name: app
      lifecycle:
        preStop:
          exec:
            command: ["/bin/sh", "-c", "sleep 10"]
```

With matching application settings:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 10s   # web server drain
  actor:
    shutdown:
      timeout: 20s                    # actor system coordinated shutdown
    pekko:
      coordinated-shutdown:
        phases:
          cluster-leave:
            timeout: 10s
          cluster-sharding-shutdown-region:
            timeout: 10s
```

Here the worst case is 10s (`preStop`) + 10s (web drain) + 20s (actor system) = 40s, inside the 45s grace period. If you must keep `terminationGracePeriodSeconds: 30` with a 10s `preStop` sleep, lower `spring.actor.shutdown.timeout` (for example to `15s`) and the web drain timeout so the sum stays under 30s.

## Health checks

With Spring Boot Actuator on the classpath, the starter adds two health indicators to `/actuator/health`. Actuator is optional: without it the starter registers no indicator and the application starts as before.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

| Contributor | When | Status |
|---|---|---|
| `actorSystem` | always | `UP` while the actor system runs, `DOWN` once it has terminated |
| `actorCluster` | cluster mode only (`spring.actor.pekko.actor.provider=cluster`) | `UP` while this member is `Up`; `OUT_OF_SERVICE` while it is joining, leaving or exiting; `DOWN` when it is unreachable, downed or removed |

The `actorCluster` details hold only counts and this node's status, never member addresses:

```json
"actorCluster": {
  "status": "UP",
  "details": { "members": 3, "unreachable": 0, "status": "Up" }
}
```

Turn both indicators off with:

```yaml
management:
  health:
    actor:
      enabled: false   # default: true
```

### Readiness group

Both indicators join the `readiness` health group by default, next to Spring Boot's `readinessState`. Spring Boot creates the `liveness` and `readiness` groups on Kubernetes, or anywhere with `management.endpoint.health.probes.enabled=true`. A node that is leaving the cluster reports `OUT_OF_SERVICE`, so `/actuator/health/readiness` returns `503` and Kubernetes stops routing traffic to it before it shuts down.

If you set `management.endpoint.health.group.readiness.include` yourself, the starter keeps your list as written; add `actorSystem` and `actorCluster` to it to keep them in the group:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState, actorSystem, actorCluster, db
```

The `liveness` group is left unchanged: a cluster problem should take the pod out of rotation, not restart it.

### Kubernetes probes

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true   # on by default when running on Kubernetes
```

```yaml
spec:
  containers:
    - name: app
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 10
        failureThreshold: 3
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 5
        periodSeconds: 3
        failureThreshold: 3
```

Keep the readiness `periodSeconds × failureThreshold` (here 9s) shorter than the `preStop` sleep above (10s), so the pod is out of the load balancer before the application starts shutting down. In cluster mode a new pod reports `OUT_OF_SERVICE` until it has joined the cluster and become `Up`, so it gets no traffic before it can reach sharded entities and singletons.
