# Integration Features Implementation Tasks

**Overall Priority:** MEDIUM
**Approach:** Library support for Spring, examples for external systems

> **Note:** WebSocket feature REMOVED per user feedback

---

## Task Breakdown

### Phase 1: Spring Events Bridge (Week 1-2)
**Priority:** MEDIUM
**Estimated Effort:** 1-2 weeks
**Approach:** Library support (wrapped implementation)

- [ ] **Task 1.1:** @SendToActor annotation (1 week)
  - File: `tasks/01-spring-events-bridge.md`
  - Event listener → Actor message conversion
  - Actor → Spring event publishing

- [ ] **Task 1.2:** Configuration & examples (2-3 days)
  - File: `tasks/02-events-configuration.md`
  - YAML configuration
  - Usage examples

### Phase 2: Kafka Integration Example (Week 3)
**Priority:** MEDIUM
**Estimated Effort:** 1-2 weeks
**Approach:** Example module (NOT library feature)

- [ ] **Task 2.1:** Kafka example module (1-2 weeks)
  - File: `tasks/03-kafka-example-module.md`
  - Complete example in `example/kafka/`
  - Consumer actor pattern
  - Producer actor pattern
  - End-to-end pipeline example

### Phase 3: gRPC Integration Example (Week 4)
**Priority:** LOW
**Estimated Effort:** 1-2 weeks
**Approach:** Example module (NOT library feature)

- [ ] **Task 3.1:** gRPC example module (1-2 weeks)
  - File: `tasks/04-grpc-example-module.md`
  - Complete example in `example/grpc/`
  - Actor as gRPC service
  - Proto definitions
  - Client/server examples

---

## Removed Features

### ❌ WebSocket Integration
**Status:** REMOVED based on user feedback
**Original Estimate:** 2-3 weeks
**Rationale:** Unclear value vs. effort

---

## What NOT to Do

**❌ DO NOT implement:**
- Kafka library-level integration (use examples)
- gRPC library-level integration (use examples)
- WebSocket integration (removed)

**✅ DO implement:**
- Spring Events bridge (valuable integration)
- Comprehensive Kafka examples
- Comprehensive gRPC examples

---

## Success Criteria

- ✅ Spring Events bridge works seamlessly
- ✅ Kafka example module demonstrates best practices
- ✅ gRPC example module demonstrates integration patterns
- ✅ Documentation explains when to use each integration
