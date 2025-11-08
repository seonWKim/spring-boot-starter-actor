# Testing Utilities Implementation Tasks

**Overall Priority:** HIGH
**Approach:** Thin wrapper over Pekko TestKit (DON'T reinvent)

---

## Task Breakdown

### Phase 1: Spring Boot TestKit Wrapper (Week 1-2)
**Priority:** HIGH
**Estimated Effort:** 2 weeks

- [ ] **Task 1.1:** @EnableActorTesting annotation (3-4 days)
  - File: `tasks/01-enable-actor-testing.md`
  - Auto-configuration for testing
  - SpringActorTestKit bean

- [ ] **Task 1.2:** Fluent test API (1 week)
  - File: `tasks/02-fluent-test-api.md`
  - Wrap Pekko TestKit with Spring-friendly API
  - `testKit.forActor().spawn().send().expectReply()`

- [ ] **Task 1.3:** Test probe support (2-3 days)
  - File: `tasks/03-test-probe-support.md`
  - ActorTestProbe wrapper
  - Message expectation helpers

### Phase 2: Mock Actor Support (Week 3)
**Priority:** HIGH
**Estimated Effort:** 1 week

- [ ] **Task 2.1:** @MockActor annotation (3-4 days)
  - File: `tasks/04-mock-actor-annotation.md`
  - Mockito integration for actor refs
  - Spring Test integration

- [ ] **Task 2.2:** MockSpringActorSystem (2-3 days)
  - File: `tasks/05-mock-actor-system.md`
  - Programmatic mocking
  - Behavior configuration

### Phase 3: Common Test Utilities (Week 4)
**Priority:** MEDIUM
**Estimated Effort:** 1 week

- [ ] **Task 3.1:** State verification helpers (2-3 days)
  - File: `tasks/06-state-verification.md`
  - assertActorState() utilities

- [ ] **Task 3.2:** Message flow testing (2-3 days)
  - File: `tasks/07-message-flow-testing.md`
  - MessageFlowTester utility

### Phase 4: Performance Testing (Deferred)
**Priority:** LOW - Nice to have
**Estimated Effort:** 1 week

- [ ] **Task 4.1:** Performance testing utilities (DEFER)
  - File: `tasks/08-performance-testing.md`
  - Throughput benchmarking
  - Latency measurement

---

## What NOT to Do

**❌ DO NOT implement:**
- Custom TestKit from scratch (wrap Pekko's)
- Complex performance testing initially
- Actor visualization in tests

**✅ DO implement:**
- Thin wrapper over Pekko TestKit
- Spring Boot integration (@EnableActorTesting)
- Mock support for unit tests

---

## Success Criteria

- ✅ @EnableActorTesting works with @SpringBootTest
- ✅ Fluent test API simplifies common patterns
- ✅ Mock actors work with Mockito
- ✅ Tests are easy to write and maintain
- ✅ No reimplementation of Pekko TestKit core
