⏺ Production-Ready Metrics for Spring Boot Actor System

🎭 Actor Metrics

- [x] actor.processing-time - Time taken to process messages (Timer)
  - **Test Strategy:** Create actor -> Send message -> Verify metric value > 0 after processing completes
- [ ] actor.time-in-mailbox - Time from message enqueue to dequeue (Timer)
  - **Test Strategy:** Create actor with slow message handler -> Send multiple messages -> Verify time-in-mailbox metric increases for queued messages
- [ ] actor.mailbox-size - Current mailbox size (Gauge/RangeSampler)
  - **Test Strategy:** Create actor with blocking handler -> Send 10 messages -> Verify mailbox size gauge shows 9 pending messages
- [ ] actor.errors - Count of processing errors (Counter)
  - **Test Strategy:** Create actor -> Send message that triggers exception -> Verify error counter increments by 1
- [ ] actor.messages.processed - Total messages processed (Counter)
  - **Test Strategy:** Create actor -> Send 5 messages -> Verify processed counter equals 5 after all messages complete
- [ ] actor.lifecycle.restarts - Actor restart count (Counter)
  - **Test Strategy:** Create supervised actor -> Trigger failure with restart strategy -> Verify restart counter increments
- [ ] actor.lifecycle.stops - Actor stop count (Counter)
  - **Test Strategy:** Create actor -> Call stop/terminate -> Verify stop counter increments by 1

📊 Actor System Metrics

- [ ] system.active-actors - Number of active actors (Gauge)
  - **Test Strategy:** Create 3 actors -> Verify gauge shows 3 -> Stop 1 actor -> Verify gauge shows 2
- [ ] system.processed-messages - Total messages processed system-wide (Counter)
  - **Test Strategy:** Create 2 actors -> Send 3 messages to each -> Verify system-wide counter shows 6
- [ ] system.dead-letters - Dead letter count (Counter)
  - **Test Strategy:** Send message to non-existent actor path -> Verify dead letter counter increments
- [ ] system.unhandled-messages - Unhandled message count (Counter)
  - **Test Strategy:** Create actor without handler for specific message type -> Send that message -> Verify unhandled counter increments
- [ ] system.created-actors.total - Total actors created (Counter)
  - **Test Strategy:** Create 5 actors sequentially -> Verify created counter equals 5
- [ ] system.terminated-actors.total - Total actors terminated (Counter)
  - **Test Strategy:** Create 3 actors -> Terminate all -> Verify terminated counter equals 3

🧵 Dispatcher/Thread Pool Metrics

- [ ] dispatcher.threads.active - Active thread count (Gauge)
  - **Test Strategy:** Create actors with blocking tasks -> Submit concurrent messages -> Verify active thread count increases
- [ ] dispatcher.threads.total - Total thread count (Gauge)
  - **Test Strategy:** Configure dispatcher with max-threads=10 -> Submit workload -> Verify total threads gauge <= 10
- [ ] dispatcher.queue.size - Task queue size (Gauge)
  - **Test Strategy:** Submit 100 tasks to limited thread pool -> Verify queue size gauge > 0 during processing
- [ ] dispatcher.tasks.completed - Completed tasks (Counter)
  - **Test Strategy:** Submit 50 tasks -> Wait for completion -> Verify completed counter equals 50
- [ ] dispatcher.tasks.submitted - Submitted tasks (Counter)
  - **Test Strategy:** Submit 20 messages to actors -> Verify submitted counter increments by 20
- [ ] dispatcher.tasks.rejected - Rejected tasks (Counter)
  - **Test Strategy:** Configure bounded queue -> Submit tasks exceeding capacity -> Verify rejected counter increments
- [ ] dispatcher.parallelism - Parallelism level (Gauge)
  - **Test Strategy:** Configure dispatcher parallelism=4 -> Verify gauge shows 4
- [ ] dispatcher.utilization - Thread pool utilization % (Gauge)
  - **Test Strategy:** Submit CPU-intensive tasks -> Verify utilization gauge approaches 100% under load

📡 Remote/Serialization Metrics (if using remote actors)

- [ ] remote.messages.inbound.size - Inbound message size (Histogram)
  - **Test Strategy:** Send remote messages of varying sizes (1KB, 10KB, 100KB) -> Verify histogram captures size distribution
- [ ] remote.messages.outbound.size - Outbound message size (Histogram)
  - **Test Strategy:** Send responses of different sizes -> Verify outbound size histogram records all sizes
- [ ] remote.serialization-time - Message serialization time (Timer)
  - **Test Strategy:** Send complex object message -> Verify serialization timer records time > 0
- [ ] remote.deserialization-time - Message deserialization time (Timer)
  - **Test Strategy:** Receive remote message with nested objects -> Verify deserialization timer captures duration
- [ ] remote.messages.inbound.count - Inbound message count (Counter)
  - **Test Strategy:** Receive 10 remote messages -> Verify inbound counter equals 10
- [ ] remote.messages.outbound.count - Outbound message count (Counter)
  - **Test Strategy:** Send 15 messages to remote actor -> Verify outbound counter equals 15

🏢 Cluster Sharding Metrics (if using sharding)

- [ ] sharding.region.hosted-shards - Shards per region (Gauge)
  - **Test Strategy:** Create sharded actors across regions -> Verify each region's shard count gauge
- [ ] sharding.region.hosted-entities - Entities per region (Gauge)
  - **Test Strategy:** Create 100 entities -> Verify sum of entities across all regions equals 100
- [ ] sharding.region.processed-messages - Messages per region (Counter)
  - **Test Strategy:** Send messages to entities in specific region -> Verify region's message counter increments
- [ ] sharding.shard.hosted-entities - Entity distribution (Histogram)
  - **Test Strategy:** Create entities with different shard keys -> Verify histogram shows entity distribution across shards
- [ ] sharding.shard.processed-messages - Message distribution (Histogram)
  - **Test Strategy:** Send messages evenly to all shards -> Verify histogram shows balanced message distribution

📬 Mailbox Metrics

- [ ] mailbox.enqueue-time - Time to enqueue message (Timer)
  - **Test Strategy:** Send message to actor -> Verify enqueue timer records time > 0
- [ ] mailbox.dequeue-time - Time to dequeue message (Timer)
  - **Test Strategy:** Actor processes message from mailbox -> Verify dequeue timer captures duration
- [ ] mailbox.size.current - Current mailbox size (Gauge)
  - **Test Strategy:** Send 5 messages to blocked actor -> Verify current size gauge shows 5
- [ ] mailbox.size.max - Max mailbox size reached (Gauge)
  - **Test Strategy:** Send varying numbers of messages -> Verify max size gauge captures peak value
- [ ] mailbox.overflow - Mailbox overflow events (Counter)
  - **Test Strategy:** Configure bounded mailbox -> Send messages exceeding capacity -> Verify overflow counter increments

⏱️ Scheduler Metrics

- [ ] scheduler.tasks.scheduled - Scheduled tasks (Counter)
  - **Test Strategy:** Schedule 10 tasks with delays -> Verify scheduled counter equals 10
- [ ] scheduler.tasks.completed - Completed scheduled tasks (Counter)
  - **Test Strategy:** Schedule 5 tasks -> Wait for execution -> Verify completed counter equals 5
- [ ] scheduler.tasks.cancelled - Cancelled tasks (Counter)
  - **Test Strategy:** Schedule task -> Cancel before execution -> Verify cancelled counter increments
- [ ] scheduler.delay.actual - Actual vs scheduled delay (Histogram)
  - **Test Strategy:** Schedule tasks with 100ms delay -> Verify histogram shows actual delay distribution around 100ms

🔍 Additional Production Metrics

- [ ] message.latency.p50/p95/p99 - Message processing percentiles (Timer)
  - **Test Strategy:** Send 1000 messages with varying processing times -> Verify p50 < p95 < p99 percentiles
- [ ] actor.stash.size - Stashed message count (Gauge)
  - **Test Strategy:** Actor stashes 3 messages during state transition -> Verify stash size gauge shows 3
- [ ] supervision.decisions - Supervision strategy decisions (Counter by decision type)
  - **Test Strategy:** Trigger failures with different strategies (restart, stop, escalate) -> Verify counters by decision type
- [ ] circuit-breaker.state - Circuit breaker states (Gauge)
  - **Test Strategy:** Trigger circuit breaker transitions (closed->open->half-open) -> Verify state gauge reflects current state
- [ ] backpressure.dropped - Dropped due to backpressure (Counter)
  - **Test Strategy:** Overwhelm actor with messages beyond capacity -> Verify dropped message counter increments

Tags/Labels to Include

- system: Actor system name
- path: Actor path
- class: Actor class name
- dispatcher: Dispatcher name
- message-type: Message class
- error-type: Exception class
- supervision-strategy: Strategy used
- router-type: Router implementation
