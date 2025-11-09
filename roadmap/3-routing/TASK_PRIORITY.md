# Routing Patterns Implementation Tasks

**Overall Priority:** HIGH
**Status:** 🚧 IN PROGRESS
**Approach:** Hybrid (Wrap Pekko routers with Spring Boot API)

---

## ✅ Completed Work

### Phase 1: Core Router Infrastructure ✅ COMPLETED
- ✅ **Task 1.1:** Router base infrastructure (see `01-router-base-infrastructure.md`)
- ✅ **Task 1.2:** Round Robin & Random strategies (see `02-basic-routing-strategies.md`)

**Test Results:** 145/145 tests passing (100%)

See `PROGRESS.md` for complete details.

### Phase 2: Advanced Routing Strategies ✅ COMPLETED
- ❌ **Task 2.1:** Smallest Mailbox strategy - NOT AVAILABLE IN PEKKO TYPED API
- ✅ **Task 2.2:** Consistent Hashing strategy - COMPLETED (2025-11-09)
- ✅ **Task 2.3:** Broadcast strategy - COMPLETED (2025-11-09)

**Implementation:**
- `BroadcastRoutingStrategy` using `withBroadcastPredicate()`
- `ConsistentHashingRoutingStrategy` using `withConsistentHashingRouting()`
- `ConsistentHashable` interface for hash key extraction
- Full test coverage in `SpringRouterBehaviorTest.java`

**Test Results:** All routing strategy tests passing (broadcast + consistent hashing)

**Note:** Smallest Mailbox routing is NOT available in Apache Pekko Typed API - only available in Classic API. All available Pekko Typed routing strategies have been implemented.

---

#### Task 2.1: Smallest Mailbox Strategy - Not Available

**Status:** ❌ NOT AVAILABLE IN PEKKO TYPED API
**Completion Date:** 2025-11-09

**Summary:**
The Smallest Mailbox routing strategy is NOT available in Apache Pekko Typed API. This strategy exists in the Pekko Classic API but has not been ported to the Typed API. The Smallest Mailbox strategy requires access to mailbox queue sizes, which is not exposed in the Typed API for type safety and simplicity.

**Available Strategies in Pekko Typed API (All Implemented):**
1. ✅ **Round Robin** - `withRoundRobinRouting()`
2. ✅ **Random** - `withRandomRouting()`
3. ✅ **Broadcast** - `withBroadcastPredicate()`
4. ✅ **Consistent Hashing** - `withConsistentHashingRouting()`

**Not Available in Typed API:**
- ❌ Smallest Mailbox
- ❌ Balancing
- ❌ Scatter-Gather-First-Completed
- ❌ Tail Chopping

**Alternative Solutions if Load-Aware Routing is Needed:**

1. **Use Round Robin (Recommended):**
   - Even distribution over time
   - No monitoring overhead
   - Works well for homogeneous workloads
   - Example:
     ```java
     return SpringRouterBehavior.builder(Command.class, ctx)
         .withRoutingStrategy(RoutingStrategy.roundRobin())
         .withPoolSize(10)
         .withWorkerActors(WorkerActor.class)
         .build();
     ```

2. **Custom Load Balancer Actor:**
   - Full control over load metrics
   - Can use custom heuristics (processing time, message type, etc.)
   - More complex implementation, manual worker lifecycle management

3. **Consistent Hashing for Session Affinity:**
   - Ensures related work goes to same worker
   - Good for stateful processing
   - Example:
     ```java
     public class ProcessOrder implements Command, ConsistentHashable {
         @Override
         public String getConsistentHashKey() {
             return customerId; // Same customer always goes to same worker
         }
     }
     ```

**Recommendation:**
Use Round Robin routing for most use cases. The Pekko team deliberately chose not to include Smallest Mailbox in the Typed API, suggesting that simpler strategies are sufficient for most scenarios.

**For Future AI Agents:**
Do not attempt to implement Smallest Mailbox routing. This strategy does not exist in Pekko Typed API and cannot be implemented using the available `PoolRouter` methods. All four available strategies have been successfully implemented and tested.

**References:**
- [Apache Pekko Typed Routers Documentation](https://pekko.apache.org/docs/pekko/current/typed/routers.html)
- [Pekko PoolRouter API](https://pekko.apache.org/api/pekko/1.0/org/apache/pekko/actor/typed/javadsl/PoolRouter.html)

---

## 🔜 Remaining Tasks

### Phase 3: Dynamic Resizing (Week 4)
**Priority:** MEDIUM
**Estimated Effort:** 1 week

- [ ] **Task 3.1:** Pool size configuration (2-3 days)
  - File: `tasks/06-pool-size-config.md`
  - Initial, min, max pool size

- [ ] **Task 3.2:** Auto-scaling based on load (3-4 days)
  - File: `tasks/07-dynamic-resizing.md`
  - Pressure-based scaling
  - Manual resize API

### Phase 4: Spring Boot Integration (Week 5)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 4.1:** YAML configuration (2-3 days)
  - File: `tasks/08-spring-boot-config.md`
  - Per-router configuration in application.yml

- ✅ **Task 4.2:** Supervision strategy integration (COMPLETED)
  - Worker supervision via `withSupervisionStrategy()`
  - Tested with restart strategy in RouterEdgeCaseTest

### Phase 5: Metrics & Monitoring (Week 6)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 5.1:** Router metrics (3-4 days)
  - File: `tasks/10-router-metrics.md`
  - Pool size, utilization, routing performance

- [ ] **Task 5.2:** Health checks & Actuator endpoints (2-3 days)
  - File: `tasks/11-router-health-monitoring.md`
  - Health indicators, management endpoints

### Phase 6: Documentation & Examples (Week 7)
**Priority:** MEDIUM
**Estimated Effort:** 1 week

- [ ] **Task 6.1:** Comprehensive documentation (3-4 days)
  - File: `tasks/12-router-documentation.md`
  - User guide, API docs, best practices

- [ ] **Task 6.2:** Example applications (2-3 days)
  - File: `tasks/13-router-examples.md`
  - Real-world routing scenarios

---

## Success Criteria

**Completed:**
- ✅ Phase 1: Core infrastructure and basic strategies (Round Robin, Random)
- ✅ Phase 2: Advanced routing strategies (Broadcast, Consistent Hashing - Smallest Mailbox N/A)

**Remaining:**
- [ ] Phase 3: Dynamic pool resizing
- [ ] Phase 4: Spring Boot YAML configuration
- [ ] Phase 5: Metrics and health checks
- [ ] Phase 6: Documentation and examples
