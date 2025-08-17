# Contributing to Spring Boot Starter Actor

Welcome to Spring Boot Starter Actor! This document outlines contribution areas and enhancement opportunities for the project.

## 🎯 Priority Tasks

### High Priority
- [x] **Timeout Configuration Alignment** - Standardize timeout handling between SpringActorRef and SpringShardedActorRef
- [x] **Builder Pattern** - Implement builder pattern for actor ref initialization
- [x] **Type Safety** - Add stricter generic bounds and remove unchecked casts
- [x] **Direct Access** - Add getUnderlying() methods for advanced use cases

### Medium Priority
- [ ] **Retry Mechanisms** - Implement retry policies with exponential backoff
- [ ] **Basic Metrics** - Add more pekko native metrics under metrics module. 

### Low Priority
- [ ] **Advanced Monitoring** - Grafana dashboards for pekko native metrics. 
- [ ] **Lifecycle Management** - Graceful shutdown and termination handling
- [ ] **Testing Utilities** - Test fixtures and mock actors
- [ ] **Circuit Breaker** - Resilience patterns implementation

## 🏗️ Core Infrastructure

### Deployment & Scalability
- [ ] Blue-Green deployment example 
- [ ] Rolling Update support - app versioning, minimizing data loss etc 
- [ ] Kubernetes deployment templates and cluster formation

### Sharding & Distribution
- [ ] Custom sharding logic for even load distribution
- [ ] Affinity-based sharding for related entities
- [ ] Dynamic shard allocation based on metrics
- [ ] Shard rebalancing strategies for cluster changes

## 🔧 API Enhancements

### Resilience & Error Handling
- [ ] Circuit breaker pattern implementation
- [ ] Error recovery strategies
- [ ] Retry policies with exponential backoff

## 📊 Monitoring & Observability

### Metrics & Tracing
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

# Run cluster 
sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
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

## 📋 Compatibility

### Version Support
- Spring Boot 2.x (core module)
- Spring Boot 3.x (core-boot3 module)
- Java 11+
- Apache Pekko (Akka fork)

Thank you for contributing to Spring Boot Starter Actor! Your efforts help make actor-based programming more accessible in the Spring ecosystem.
