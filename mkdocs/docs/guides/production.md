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

With Spring Boot Actuator on the classpath, `/actuator/health` gets two components:

| Component | When | Status |
|---|---|---|
| `actorSystem` | always | `UP` while the actor system runs, `DOWN` once it has terminated |
| `actorCluster` | `spring.actor.provider=cluster` | `UP` when this node is an `Up` member; `OUT_OF_SERVICE` while it has not joined yet, is joining, weakly up, leaving or shutting down; `DOWN` when it was downed or removed, or other members report it unreachable |

`actorCluster` details hold this node's status, the member count and the unreachable count, never addresses. An unreachable peer shows only in the count: one partitioned peer does not make every node unhealthy. `management.health.actor.enabled=false` turns both off.

Neither is in the Kubernetes probe groups by default; add `actorCluster` to readiness so a pod gets traffic only once it is a cluster member, and stops getting it as soon as it leaves:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,actorCluster
```

Keep it out of liveness: a node that is still joining is not broken, and restarting it would only delay the join. Cluster Bootstrap reaches pods through Pekko Management, not through the readiness-gated Service, so a cluster still forms while every pod is not ready yet.

## Cluster metrics

With `actor-metrics` and Micrometer on the classpath (for example through `spring-boot-starter-actuator`), cluster mode adds these meters. They need no Java agent.

| Meter | Type | What |
|---|---|---|
| `actor.cluster.members` | gauge | Members this node sees |
| `actor.cluster.unreachable` | gauge | Of them, how many are unreachable |
| `actor.cluster.leader` | gauge | `1` while this node is the leader, else `0` |
| `actor.cluster.events` | counter, tag `event` | Cluster events this node received after the application started: `MemberUp`, `MemberRemoved`, `UnreachableMember` and the other `ClusterEvent` types. Every type has a series from the start, so a dashboard sees `0`, not a gap |

`spring.actor.metrics.enabled=false` turns them off. Alert on `actor.cluster.unreachable > 0` for longer than the split brain resolver's `stable-after`, and on a `members` count below the replica count.
