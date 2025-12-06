# Cluster Example

Demonstrates cluster sharding and cluster singletons using the Spring Boot Actor framework.

## Quick Start

```bash
# Start 3-node cluster (from project root)
sh cluster-start.sh cluster io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Stop cluster
sh cluster-stop.sh
```

Starts nodes on ports 8080, 8081, 8082 with logs in `log_cluster_*.txt`

### Manual Start (Alternative)

Start each node in a separate terminal:

```bash
# Node 1
./gradlew :example:cluster:bootRun

# Node 2
./gradlew :example:cluster:bootRun --args='--server.port=8081 --spring.actor.pekko.remote.artery.canonical.port=2552'

# Node 3
./gradlew :example:cluster:bootRun --args='--server.port=8082 --spring.actor.pekko.remote.artery.canonical.port=2553'
```

## Examples

### 1. Cluster Sharding

**Files:** `HelloActor.java`, `HelloService.java`, `HelloController.java`

**Test:**
```bash
# Different entities distributed across nodes
curl "http://localhost:8080/hello?message=Hello&entityId=entity-1"
curl "http://localhost:8081/hello?message=World&entityId=entity-2"
curl "http://localhost:8082/hello?message=Test&entityId=entity-3"

# Same entity always on same node
curl "http://localhost:8080/hello?message=First&entityId=shared"
curl "http://localhost:8081/hello?message=Second&entityId=shared"  # Same node as above
```

### 2. Cluster Singleton

**Files:** `ClusterMetricsAggregator.java`, `ClusterSingletonService.java`, `ClusterSingletonController.java`

**Test:**
```bash
# Record metrics from different nodes
curl -X POST "http://localhost:8080/api/singleton/metrics?name=requests&value=100"
curl -X POST "http://localhost:8081/api/singleton/metrics?name=requests&value=200"

# Get aggregated metrics (from any node)
curl http://localhost:8080/api/singleton/metrics

# Check which node hosts singleton
curl http://localhost:8080/api/singleton/metrics | jq .singletonNodeAddress

# Reset metrics
curl -X DELETE http://localhost:8080/api/singleton/metrics
```

### 3. Failover Test

```bash
# Identify singleton host node
curl http://localhost:8080/api/singleton/metrics | jq .singletonNodeAddress

# Record metric
curl -X POST "http://localhost:8080/api/singleton/metrics?name=test&value=42"

# Kill that node (if port 8080)
# Wait 5-10 seconds

# Verify singleton migrated
curl http://localhost:8081/api/singleton/metrics | jq .singletonNodeAddress
```

## Implementation

### Spawn Cluster Singleton

```java
SpringActorHandle<Command> singleton = actorSystem
    .actor(MySingletonActor.class)
    .withId("my-singleton")
    .asClusterSingleton()
    .spawn()
    .toCompletableFuture()
    .get();
```

### Use Sharded Actor

```java
SpringShardedActorHandle<Command> entity = actorSystem
    .sharded(MyShardedActor.class)
    .withId("entity-123")
    .get();

entity.tell(new MyCommand());
```

## Architecture

**Sharding:** Entities distributed across nodes, same ID always on same node

**Singleton:** ONE instance in cluster, proxy on all nodes routes messages to it

## Configuration

Key settings in `application.yml`:

```yaml
spring.actor.pekko:
  actor.provider: cluster
  remote.artery.canonical:
    hostname: 127.0.0.1
    port: 2551
  cluster.seed-nodes:
    - pekko://spring-pekko-example@127.0.0.1:2551
    - pekko://spring-pekko-example@127.0.0.1:2552
    - pekko://spring-pekko-example@127.0.0.1:2553
```

## Troubleshooting

- **Cluster not forming?** Wait 10-20s, check seed nodes match, verify same actor system name
- **Singleton not starting?** Ensure `actor.provider=cluster` and cluster formed
- **Timeouts?** Verify messages implement `JsonSerializable`, check Jackson annotations

## Resources

- [Framework Documentation](../../README.md)
- [Pekko Cluster Docs](https://pekko.apache.org/docs/pekko/current/typed/cluster.html)
- [Cluster Singleton Docs](https://pekko.apache.org/docs/pekko/current/typed/cluster-singleton.html)
