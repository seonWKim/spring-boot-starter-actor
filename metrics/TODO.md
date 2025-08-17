⏺ Production-Ready Metrics for Spring Boot Actor System

🎭 Actor Metrics

- [ ] actor.processing-time - Time taken to process messages (Timer)
- [ ] actor.time-in-mailbox - Time from message enqueue to dequeue (Timer)
- [ ] actor.mailbox-size - Current mailbox size (Gauge/RangeSampler)
- [ ] actor.errors - Count of processing errors (Counter)
- [ ] actor.messages.processed - Total messages processed (Counter)
- [ ] actor.lifecycle.restarts - Actor restart count (Counter)
- [ ] actor.lifecycle.stops - Actor stop count (Counter)

📊 Actor System Metrics

- [ ] system.active-actors - Number of active actors (Gauge)
- [ ] system.processed-messages - Total messages processed system-wide (Counter)
- [ ] system.dead-letters - Dead letter count (Counter)
- [ ] system.unhandled-messages - Unhandled message count (Counter)
- [ ] system.created-actors.total - Total actors created (Counter)
- [ ] system.terminated-actors.total - Total actors terminated (Counter)

🧵 Dispatcher/Thread Pool Metrics

- [ ] dispatcher.threads.active - Active thread count (Gauge)
- [ ] dispatcher.threads.total - Total thread count (Gauge)
- [ ] dispatcher.queue.size - Task queue size (Gauge)
- [ ] dispatcher.tasks.completed - Completed tasks (Counter)
- [ ] dispatcher.tasks.submitted - Submitted tasks (Counter)
- [ ] dispatcher.tasks.rejected - Rejected tasks (Counter)
- [ ] dispatcher.parallelism - Parallelism level (Gauge)
- [ ] dispatcher.utilization - Thread pool utilization % (Gauge)

📡 Remote/Serialization Metrics (if using remote actors)

- [ ] remote.messages.inbound.size - Inbound message size (Histogram)
- [ ] remote.messages.outbound.size - Outbound message size (Histogram)
- [ ] remote.serialization-time - Message serialization time (Timer)
- [ ] remote.deserialization-time - Message deserialization time (Timer)
- [ ] remote.messages.inbound.count - Inbound message count (Counter)
- [ ] remote.messages.outbound.count - Outbound message count (Counter)

🏢 Cluster Sharding Metrics (if using sharding)

- [ ] sharding.region.hosted-shards - Shards per region (Gauge)
- [ ] sharding.region.hosted-entities - Entities per region (Gauge)
- [ ] sharding.region.processed-messages - Messages per region (Counter)
- [ ] sharding.shard.hosted-entities - Entity distribution (Histogram)
- [ ] sharding.shard.processed-messages - Message distribution (Histogram)

📬 Mailbox Metrics

- [ ] mailbox.enqueue-time - Time to enqueue message (Timer)
- [ ] mailbox.dequeue-time - Time to dequeue message (Timer)
- [ ] mailbox.size.current - Current mailbox size (Gauge)
- [ ] mailbox.size.max - Max mailbox size reached (Gauge)
- [ ] mailbox.overflow - Mailbox overflow events (Counter)

⏱️ Scheduler Metrics

- [ ] scheduler.tasks.scheduled - Scheduled tasks (Counter)
- [ ] scheduler.tasks.completed - Completed scheduled tasks (Counter)
- [ ] scheduler.tasks.cancelled - Cancelled tasks (Counter)
- [ ] scheduler.delay.actual - Actual vs scheduled delay (Histogram)

🔍 Additional Production Metrics

- message.latency.p50/p95/p99 - Message processing percentiles (Timer)
- actor.stash.size - Stashed message count (Gauge)
- supervision.decisions - Supervision strategy decisions (Counter by decision type)
- circuit-breaker.state - Circuit breaker states (Gauge)
- backpressure.dropped - Dropped due to backpressure (Counter)

Tags/Labels to Include

- system: Actor system name
- path: Actor path
- class: Actor class name
- dispatcher: Dispatcher name
- message-type: Message class
- error-type: Exception class
- supervision-strategy: Strategy used
- router-type: Router implementation
