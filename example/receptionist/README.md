# Receptionist Example - Dynamic Worker Pool

This example demonstrates how to use Pekko's **Receptionist** feature for dynamic actor discovery and service registration.

## Overview

The Receptionist provides a service registry pattern for actors, enabling:
- **Dynamic service discovery** - Find actors by service type
- **Worker pools** - Multiple workers registered under the same key
- **Load balancing** - Distribute work across available workers
- **Availability monitoring** - Subscribe to changes in service availability

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkerPoolController                      │
│                        (REST API)                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    WorkerPoolService                         │
│    - Manages worker pool using Receptionist                  │
│    - Round-robin load balancing                              │
│    - Subscribes to pool changes                              │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│            SpringReceptionistService                         │
│       - register(serviceKey, actorRef)                       │
│       - find(serviceKey)                                     │
│       - subscribe(serviceKey, callback)                      │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌─────────┐     ┌─────────┐     ┌─────────┐
    │ Worker1 │     │ Worker2 │     │ Worker3 │
    └─────────┘     └─────────┘     └─────────┘
```

## Running the Example

### Start the Application

```bash
cd example/receptionist
../../gradlew bootRun
```

The server will start on http://localhost:8080

### API Usage Examples

#### 1. Check Worker Pool Status

```bash
curl http://localhost:8080/pool/status
```

Response:
```json
{
  "workerCount": 3,
  "workerIds": ["worker-1", "worker-2", "worker-3"]
}
```

#### 2. Process a Single Batch

```bash
curl -X POST http://localhost:8080/process?records=20
```

Response:
```json
{
  "batchId": "batch-1",
  "workerId": "worker-2",
  "recordsProcessed": 20,
  "processingTimeMs": 234,
  "success": true
}
```

#### 3. Process Multiple Batches Concurrently

```bash
curl -X POST http://localhost:8080/process/bulk \
  -H "Content-Type: application/json" \
  -d "[10, 15, 20, 12, 18]"
```

This distributes batches across workers using round-robin load balancing.

#### 4. Add More Workers

```bash
curl -X POST http://localhost:8080/pool/workers
```

Response:
```json
{
  "workerId": "worker-4",
  "message": "Worker worker-4 added to pool"
}
```

## Key Concepts

### Service Key

A `ServiceKey` identifies a type of service in the receptionist registry:

```java
ServiceKey<WorkerCommand> workerKey = 
    ServiceKey.create(WorkerCommand.class, "data-processor-pool");
```

### Registration

Actors register themselves with the receptionist:

```java
receptionist.register(workerKey, workerRef);
```

### Discovery

Find all actors registered under a service key:

```java
receptionist.find(workerKey).thenAccept(listing -> {
    Set<SpringActorRef<WorkerCommand>> workers = listing.getServiceInstances();
    // Use workers...
});
```

### Subscription

Subscribe to changes in service availability:

```java
receptionist.subscribe(workerKey, listing -> {
    System.out.println("Worker pool changed: " + listing.size() + " workers");
});
```

## Pattern: Round-Robin Load Balancing

The example demonstrates a simple round-robin load balancing pattern:

```java
List<SpringActorRef<Command>> workers = new ArrayList<>(listing.getServiceInstances());
int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % workers.size());
SpringActorRef<Command> selectedWorker = workers.get(index);
```

## Benefits Over Traditional Approaches

| Traditional Approach | With Receptionist |
|---------------------|-------------------|
| Hard-coded actor names | Dynamic discovery |
| Manual tracking of actors | Automatic registry |
| Static pool size | Dynamic scaling |
| No availability monitoring | Built-in subscriptions |
| Complex failover logic | Automatic deregistration |

## Use Cases

This pattern is ideal for:
- **Worker pools** - Distribute tasks across multiple workers
- **Service discovery** - Find available services dynamically
- **Load balancing** - Balance load across service instances
- **Microservices** - Service registry in distributed systems
- **Plugin systems** - Discover and use plugin actors
- **Failover** - Automatic removal of failed services

## Logs to Observe

When running, you'll see logs showing:
- Worker registration
- Pool size changes
- Batch assignments
- Processing completion

```
INFO  WorkerPoolService : Initializing worker pool with 3 workers...
INFO  WorkerPoolService : Worker pool changed: 3 workers available
INFO  DataProcessorWorker : Worker worker-1 started and ready to process batches
INFO  WorkerPoolService : Submitting batch-1 to worker pool (selected index 0)
INFO  DataProcessorWorker : Worker worker-1 completed batch-1 in 156ms
```

## Extending the Example

Ideas for extending this example:
1. **Metrics** - Track worker utilization and throughput
2. **Priorities** - Implement priority-based task routing
3. **Health checks** - Periodic health checks for workers
4. **Adaptive scaling** - Auto-scale based on queue depth
5. **Cluster mode** - Distribute workers across cluster nodes
