# Contributing to Spring Boot Starter Actor

Thank you for considering contributing to Spring Boot Starter Actor! This document outlines the key TODOs for contributors.

## Technical TODOs

### Graceful Deployment TODOs
- [ ] Implement state migration between versions without data loss
- [ ] Maintain backward compatibility for rolling updates
- [ ] Add message versioning to handle different formats during deployment
- [ ] Ensure actors handle cluster topology changes gracefully
- [ ] Implement proper shutdown hooks for graceful termination

### Maintainability and Readability TODOs
- [ ] Use consistent naming conventions for actors, messages, and methods
- [ ] Implement comprehensive error handling and recovery strategies
- [ ] Use appropriate logging levels with contextual information
- [ ] Document actor behaviors, message flows, and state transitions

### Custom Sharding TODOs
- [ ] Implement custom sharding logic to distribute load evenly and avoid hot spots
- [ ] Add affinity-based sharding to group related entities on the same shard
- [ ] Develop dynamic shard allocation based on load metrics
- [ ] Design strategies for rebalancing shards when nodes join or leave the cluster

### Kubernetes Integration TODOs
- [ ] Create Kubernetes deployment templates for actor-based applications
- [ ] Design Kubernetes-aware cluster formation for actor systems

### Monitoring and Observability TODOs
- [ ] Implement a dashboard to monitor actors across nodes in a cluster
- [ ] Create actor system health metrics 
- [ ] Develop tracing capabilities for message flows between actors
- [ ] Implement metrics and monitoring hooks

### Additional TODOs
- [ ] Serialization best practices 
- [ ] Add performance benchmarks for critical components
