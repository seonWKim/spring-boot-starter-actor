# Streams and Backpressure Tasks

**Overall Priority:** LOW (Reconsider need)
**Approach:** Examples first, library features ONLY if examples too complex

> **🚨 CRITICAL RECOMMENDATION:** Don't reimplement Pekko Streams. Start with examples.

---

## Phase 1: Pekko Streams Examples (Week 1-2)
**Priority:** MEDIUM
**Estimated Effort:** 2-3 weeks
**Approach:** Examples showing Pekko Streams + Actors

- [ ] **Task 1.1:** Basic stream-to-actor examples (1 week)
  - File: `tasks/01-pekko-streams-examples.md`
  - Source → Actor processing → Sink
  - Backpressure handling examples
  - Error recovery patterns

- [ ] **Task 1.2:** Advanced integration patterns (1 week)
  - File: `tasks/02-advanced-stream-patterns.md`
  - Actor as source
  - Actor as sink
  - Bi-directional streams (ask pattern)

- [ ] **Task 1.3:** Production patterns documentation (2-3 days)
  - File: `tasks/03-stream-production-patterns.md`
  - Batch processing
  - Throttling strategies
  - Monitoring streams

### Decision Point: Evaluate if Wrapper Needed

After Phase 1 examples are complete, evaluate:
- Are examples too complex for users?
- Are there repetitive patterns that could be abstracted?
- Would a thin wrapper add significant value?

**If YES → Proceed to Phase 2**
**If NO → STOP here, examples are sufficient**

---

## Phase 2: Thin Wrapper (ONLY if needed)
**Priority:** LOW
**Estimated Effort:** 2-3 weeks

- [ ] **Task 2.1:** Spring Boot YAML configuration helper (1 week)
  - File: `tasks/04-stream-config-wrapper.md`
  - YAML-based stream configuration
  - ActorMaterializer configuration

- [ ] **Task 2.2:** Actor integration helpers (1 week)
  - File: `tasks/05-actor-integration-helpers.md`
  - Helper methods for common patterns
  - NOT a full stream builder API

---

## Throttling Approach

**DO:** Wrap Pekko's throttling with Spring Boot config
**DON'T:** Reimplement rate limiting algorithms

- [ ] **Task 3.1:** Throttling configuration wrapper (3-4 days)
  - File: `tasks/06-throttling-wrapper.md`
  - YAML configuration for Pekko throttling
  - Metrics on top of Pekko's throttling

---

## What NOT to Do

**❌ DO NOT implement:**
- Custom stream builder API (use Pekko's)
- Custom backpressure handling (use Pekko's)
- Custom stream operators (use Pekko's)
- Library-native throttling from scratch

**✅ DO implement:**
- Clear examples using Pekko Streams
- Thin configuration wrapper (if needed)
- Integration patterns documentation

---

## Success Criteria

**Phase 1 (Examples):**
- ✅ Comprehensive Pekko Streams examples
- ✅ Common patterns documented
- ✅ Users can use Pekko Streams with actors easily

**Phase 2 (If needed):**
- ✅ Minimal wrapper that simplifies common cases
- ✅ Does NOT hide Pekko Streams API
- ✅ Configuration-driven only
