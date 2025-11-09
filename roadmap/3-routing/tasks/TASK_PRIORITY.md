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

---

## 🔜 Remaining Tasks

### Phase 2: Advanced Routing Strategies (Week 3)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 2.1:** Smallest Mailbox strategy (2-3 days)
  - File: `tasks/03-smallest-mailbox-routing.md`
  - Load-aware routing

- [ ] **Task 2.2:** Consistent Hashing strategy (3-4 days)
  - File: `tasks/04-consistent-hashing-routing.md`
  - Session affinity support

- [ ] **Task 2.3:** Broadcast strategy (1-2 days)
  - File: `tasks/05-broadcast-routing.md`
  - Send to all workers

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

**Completed:** Phase 1 - Core infrastructure and basic strategies (see `PROGRESS.md`)

**Remaining:**
- [ ] Phase 2: Advanced routing strategies (Smallest Mailbox, Consistent Hashing, Broadcast)
- [ ] Phase 3: Dynamic pool resizing
- [ ] Phase 4: Spring Boot YAML configuration
- [ ] Phase 5: Metrics and health checks
- [ ] Phase 6: Documentation and examples
