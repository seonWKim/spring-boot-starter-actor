# Contributing to Spring Boot Starter Actor

Welcome to Spring Boot Starter Actor! This document outlines contribution areas and enhancement opportunities for the project.

## 🎯 Priority Tasks

### High Priority
- [ ] **Timeout Configuration Alignment** - Standardize timeout handling between SpringActorRef and SpringShardedActorRef
- [ ] **Builder Pattern** - Implement builder pattern for actor ref initialization
- [ ] **Type Safety** - Add stricter generic bounds and remove unchecked casts
- [ ] **Direct Access** - Add getUnderlying() methods for advanced use cases

### Medium Priority
- [ ] **Async Operations** - Add async tell methods and batch operations
- [ ] **Retry Mechanisms** - Implement retry policies with exponential backoff
- [ ] **Spring Integration** - Add @ConfigurationProperties and auto-configuration
- [ ] **Reactive Support** - Add Mono/Flux wrapper methods
- [ ] **Basic Metrics** - Integrate with Micrometer for observability

### Low Priority
- [ ] **Advanced Monitoring** - Grafana dashboards and distributed tracing
- [ ] **Lifecycle Management** - Graceful shutdown and termination handling
- [ ] **Testing Utilities** - Test fixtures and mock actors
- [ ] **Circuit Breaker** - Resilience patterns implementation

## 🏗️ Core Infrastructure

### Deployment & Scalability
- [ ] State migration between versions without data loss
- [ ] Backward compatibility for rolling updates
- [ ] Message versioning for deployment compatibility
- [ ] Cluster topology change handling
- [ ] Proper shutdown hooks and graceful termination
- [ ] Kubernetes deployment templates and cluster formation

### Sharding & Distribution
- [ ] Custom sharding logic for even load distribution
- [ ] Affinity-based sharding for related entities
- [ ] Dynamic shard allocation based on metrics
- [ ] Shard rebalancing strategies for cluster changes

## 🔧 API Enhancements

### Configuration & Initialization
- [ ] Configurable timeout constructor for SpringShardedActorRef
- [ ] ActorProperties configuration class with @ConfigurationProperties
- [ ] SpringActorRefFactory bean for centralized creation
- [ ] Application.yml/properties support

### Messaging Patterns
- [ ] **Retry Support**: askWithRetry() with configurable policies
- [ ] **Reactive Wrappers**: askMono(), askFlux(), tellMono() methods
- [ ] **Streaming Support**: Backpressure handling for reactive streams
- [ ] **Batch Operations**: tellAll() and askAll() for bulk messages

### Resilience & Error Handling
- [ ] Custom exception hierarchy (ActorTimeoutException, ActorNotAvailableException)
- [ ] Circuit breaker pattern implementation
- [ ] Error recovery strategies
- [ ] Retry policies with exponential backoff

## 📊 Monitoring & Observability

### Metrics & Tracing
- [ ] ActorMetrics interface with Micrometer integration
- [ ] Message flow tracing between actors
- [ ] Timeout and failure metrics tracking
- [ ] Actor system health indicators
- [ ] Distributed tracing support

### Dashboards & Visualization
- [ ] Actor cluster monitoring dashboard
- [ ] Grafana dashboard templates
- [ ] Performance benchmarking tools

## 📚 Documentation & Testing

### Documentation
- [ ] Comprehensive JavaDoc for all public APIs
- [ ] User guide with practical examples
- [ ] Migration guide from raw Pekko
- [ ] Best practices and anti-patterns
- [ ] Troubleshooting guide

### Testing Infrastructure
- [ ] TestSpringActorRef and TestSpringShardedActorRef for unit testing
- [ ] ActorTestKit integration
- [ ] Test fixtures and behavior verification
- [ ] Performance benchmarks
- [ ] Memory leak detection tests

## 🚀 Getting Started

### Development Setup
```bash
# Clone and build
git clone https://github.com/seonwkim/spring-boot-starter-actor.git
cd spring-boot-starter-actor
./gradlew clean build

# Run tests
./gradlew runTest

# Run examples
./gradlew :example:simple:bootRun
```

### Contribution Guidelines

1. **Code Style**
   - Follow existing patterns and conventions
   - Add comprehensive JavaDoc comments
   - Ensure type safety with proper generics
   - Write clean, testable code

2. **Testing Requirements**
   - Unit tests for all new features
   - Integration tests for Spring Boot context
   - Performance tests for critical paths
   - Thread safety verification

3. **Pull Request Checklist**
   - [ ] Code follows project conventions
   - [ ] Tests pass locally
   - [ ] Documentation updated
   - [ ] Examples still work
   - [ ] No breaking changes (or properly documented)

## 📋 Migration & Compatibility

### Breaking Changes Management
- [ ] Document all breaking changes clearly
- [ ] Provide migration examples and scripts
- [ ] Create compatibility layer when possible
- [ ] Update all example projects
- [ ] Follow semantic versioning

### Version Support
- Spring Boot 2.x (core module)
- Spring Boot 3.x (core-boot3 module)
- Java 11+
- Apache Pekko (Akka fork)

## 💡 Known Issues & Solutions

### Current Limitations
1. **SpringShardedActorRef** has hardcoded 3-second timeout
2. Missing async variants of tell() method
3. No built-in retry mechanisms
4. Limited Spring reactive integration
5. Unchecked casts to RecipientRef

### Proposed Solutions
These limitations are addressed in the priority tasks above. Contributors should focus on high-priority items first to resolve the most impactful issues.

## 📞 Contact & Support

- **Issues**: [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues)
- **Discussions**: Use GitHub Discussions for questions and ideas
- **License**: Apache License 2.0

---

Thank you for contributing to Spring Boot Starter Actor! Your efforts help make actor-based programming more accessible in the Spring ecosystem.
