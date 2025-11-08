# Feature Roadmap

This directory contains the comprehensive feature roadmap for spring-boot-starter-actor.

## Files

- **[FEATURES_ROADMAP.md](FEATURES_ROADMAP.md)** - Main index with overview and design decisions
- **[TODO.md](TODO.md)** - Development tracking and timeline
- **[FEATURES_SUMMARY.md](FEATURES_SUMMARY.md)** - Quick summary (in parent directory)

## Category Files

Each category has a dedicated markdown file with detailed specifications:

1. **[1_PERSISTENCE_AND_EVENT_SOURCING.md](1_PERSISTENCE_AND_EVENT_SOURCING.md)** - Manual state management with adapter patterns
2. **[2_STREAMS_AND_BACKPRESSURE.md](2_STREAMS_AND_BACKPRESSURE.md)** - Fluent builder API and library-native throttling
3. **[3_ROUTING_PATTERNS.md](3_ROUTING_PATTERNS.md)** - Load balancing with hybrid Pekko wrapper approach
4. **[4_TESTING_UTILITIES.md](4_TESTING_UTILITIES.md)** - Test utilities extracted from common patterns
5. **[5_ADVANCED_CLUSTERING.md](5_ADVANCED_CLUSTERING.md)** - Enterprise clustering with explicit tests
6. **[6_ENHANCED_OBSERVABILITY.md](6_ENHANCED_OBSERVABILITY.md)** - Metrics, health checks, and tracing
7. **[7_PERFORMANCE_AND_RESILIENCE.md](7_PERFORMANCE_AND_RESILIENCE.md)** - Circuit breaker, retry, deduplication
8. **[8_DEVELOPER_EXPERIENCE.md](8_DEVELOPER_EXPERIENCE.md)** - Error messages, hot reload, visualization
9. **[9_INTEGRATION_FEATURES.md](9_INTEGRATION_FEATURES.md)** - Spring Events, WebSocket, Kafka/gRPC examples
10. **[10_SECURITY_AND_COMPLIANCE.md](10_SECURITY_AND_COMPLIANCE.md)** - TLS/SSL, auth, audit logging, encryption

## Quick Start

1. **For Overview**: Read [FEATURES_ROADMAP.md](FEATURES_ROADMAP.md)
2. **For Implementation Status**: Check [TODO.md](TODO.md)
3. **For Detailed Specs**: Read individual category files
4. **For Quick Reference**: See [FEATURES_SUMMARY.md](FEATURES_SUMMARY.md) in parent directory

## Design Principles

All features follow these principles:

- **Spring Boot Native**: Leverage Spring ecosystem
- **Explicit over Implicit**: User control
- **Production Ready**: Metrics, health checks, reliability
- **Developer Friendly**: Fluent APIs, clear errors
- **Pragmatic**: Library support where valuable, examples where flexible

## Contributing

See [CONTRIBUTION.md](../CONTRIBUTION.md) and [TODO.md](TODO.md) for how to contribute.
