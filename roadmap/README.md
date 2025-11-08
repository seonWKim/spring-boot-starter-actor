# Feature Roadmap

This directory contains the comprehensive feature roadmap for spring-boot-starter-actor, organized for AI agent implementation.

## 🎯 Start Here

**👉 [ROADMAP.md](ROADMAP.md)** - Complete implementation roadmap (all-in-one)

## 📁 Implementation Directories

Each feature area has been organized into individual directories with AI-agent-ready tasks:

### 🚨 Critical Priority
- **[6-observability/](6-observability/)** - Metrics, health checks (CRITICAL - Only 1/50+ metrics done!)
- **[7-resilience/](7-resilience/)** - Circuit breaker, deduplication, retry (HIGH)

### ⚡ High Priority
- **[3-routing/](3-routing/)** - Router patterns (Excellent design, proceed as-is)
- **[4-testing/](4-testing/)** - Testing utilities (Wrap Pekko TestKit)
- **[5-clustering/](5-clustering/)** - Advanced clustering (Split brain testing CRITICAL)
- **[8-developer-experience/](8-developer-experience/)** - Error messages (HIGH DX impact)
- **[10-security/](10-security/)** - TLS/SSL, auth, audit logging

### 📚 Medium Priority
- **[1-persistence/](1-persistence/)** - Persistence patterns (Documentation focus, not library features)
- **[9-integration/](9-integration/)** - Spring Events, Kafka/gRPC examples

### 🔍 Low Priority / Reconsider
- **[2-streams/](2-streams/)** - Streams & backpressure (Use Pekko Streams + examples)

## 🚀 Quick Start

### For Implementers
1. **Read:** [ROADMAP.md](ROADMAP.md) - Complete roadmap
2. **Start:** Navigate to `6-observability/` (Phase 0 - CRITICAL)
3. **Tasks:** Check `tasks/TASK_PRIORITY.md` in each directory
4. **Implement:** Follow detailed task specifications

### For Quick Reference
- **Timeline:** 14-16 months (optimized from 18-21)
- **Phase 0:** Metrics completion (4-6 weeks) - DO THIS FIRST
- **Current State:** Only 1/50+ metrics implemented

## 📊 Task Organization

Each feature directory contains:
```
feature-name/
├── README.md              # Feature overview with review notes
└── tasks/
    ├── TASK_PRIORITY.md   # Priority order and time estimates
    └── XX-task-name.md    # Detailed task specifications
```

## ⏱️ Implementation Phases

1. **Phase 0 (CRITICAL):** Metrics completion - 4-6 weeks
2. **Phase 1:** Core resilience - 8-10 weeks
3. **Phase 2:** Advanced features - 10-12 weeks
4. **Phase 3:** Integration & docs - 6-8 weeks
5. **Phase 4:** Clustering & security - 8-10 weeks

**Total:** 14-16 months (20% faster than original plan)

## 📋 What's Inside

Each feature directory (`1-persistence/` through `10-security/`) contains:
- `README.md` - Feature overview with recommendations
- `tasks/TASK_PRIORITY.md` - Task breakdown with estimates

See [ROADMAP.md](ROADMAP.md) for complete details.
