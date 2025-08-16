# SpringActorRef and SpringShardedActorRef API Enhancements

## Current Usability Issues

### 1. Inconsistent Timeout Configuration
- **SpringActorRef**: Allows configurable default timeout via constructor
- **SpringShardedActorRef**: Hard-coded 3-second timeout with no configuration option
- Users must specify timeout for every call in SpringShardedActorRef

### 2. Limited Convenience Methods
- No async variants of tell() method
- Missing retry mechanisms for resilient messaging
- No batch operation support
- Lack of lifecycle management helpers

### 3. Type Safety Concerns
- Unchecked casts to RecipientRef in both classes
- Generic type bounds could be more restrictive

### 4. Missing Spring Integration Features
- No @ConfigurationProperties support for timeout configuration
- Limited integration with Spring's reactive stack
- No built-in observability hooks

## Enhancement Tasks

### 1. Align Timeout Configuration

- [ ] Add configurable timeout constructor to SpringShardedActorRef
- [ ] Create ActorProperties configuration class with @ConfigurationProperties
- [ ] Add defaultTimeout field to SpringShardedActorRef
- [ ] Update all ask methods in SpringShardedActorRef to use configurable timeout

### 2. Builder Pattern Implementation

- [ ] Create Builder inner class for SpringActorRef
- [ ] Create Builder inner class for SpringShardedActorRef
- [ ] Add withTimeout() method to builders
- [ ] Add withRetry() method to builders
- [ ] Add withMetrics() method to builders
- [ ] Implement build() method with validation

### 3. Enhanced Convenience Methods

#### Async Tell Operations
- [ ] Add tellAsync() method to SpringActorRef
- [ ] Add tellAsync() method to SpringShardedActorRef
- [ ] Add unit tests for async tell operations

#### Retry Support
- [ ] Create RetryPolicy interface
- [ ] Implement exponential backoff retry policy
- [ ] Add askWithRetry() method to SpringActorRef
- [ ] Add askWithRetry() method to SpringShardedActorRef
- [ ] Add configuration for max retry attempts and delays
- [ ] Add unit tests for retry mechanisms

#### Batch Operations
- [ ] Add tellAll() method for batch messages to SpringActorRef
- [ ] Add tellAll() method for batch messages to SpringShardedActorRef
- [ ] Add askAll() method for parallel asks to SpringActorRef
- [ ] Add askAll() method for parallel asks to SpringShardedActorRef
- [ ] Add performance tests for batch operations

### 4. Spring Configuration Integration

- [ ] Create ActorRefAutoConfiguration class
- [ ] Add @ConditionalOnProperty for actor.enabled
- [ ] Create ActorDefaults configuration bean
- [ ] Create SpringActorRefFactory bean
- [ ] Add application.yml/properties support
- [ ] Create configuration documentation
- [ ] Add Spring Boot starter module
- [ ] Add auto-configuration tests

### 5. Observability Enhancements

- [ ] Create ActorMetrics interface
- [ ] Implement MicrometerActorMetrics with Micrometer integration
- [ ] Add metrics field to SpringActorRef
- [ ] Add metrics field to SpringShardedActorRef
- [ ] Instrument ask() methods with metrics recording
- [ ] Instrument tell() methods with metrics recording
- [ ] Add timeout metrics tracking
- [ ] Create Grafana dashboard template
- [ ] Add metrics documentation
- [ ] Add integration tests with metrics verification

### 6. Reactive Wrapper Methods

- [ ] Add askMono() method to SpringActorRef
- [ ] Add askMono() method to SpringShardedActorRef
- [ ] Add askFlux() method for streaming to SpringActorRef
- [ ] Add askFlux() method for streaming to SpringShardedActorRef
- [ ] Add tellMono() method with completion signal
- [ ] Add reactive timeout handling
- [ ] Add backpressure support for streaming
- [ ] Add reactive examples and documentation
- [ ] Add integration tests with WebFlux

### 7. Direct Access Methods

- [ ] Add getUnderlying() method to SpringActorRef
- [ ] Add getUnderlying() method to SpringShardedActorRef
- [ ] Add getPath() convenience method
- [ ] Add documentation for advanced use cases

### 8. Lifecycle Management

- [ ] Create ActorLifecycle interface
- [ ] Add gracefulStop() method to SpringActorRef
- [ ] Add isTerminated() method to SpringActorRef
- [ ] Add watchTermination() method to SpringActorRef
- [ ] Implement PoisonPill handling
- [ ] Add lifecycle hooks for Spring context
- [ ] Add shutdown timeout configuration
- [ ] Add lifecycle tests
- [ ] Document proper shutdown procedures

## Additional Enhancements

### 9. Type Safety Improvements
- [ ] Add stricter generic bounds to SpringActorRef
- [ ] Add stricter generic bounds to SpringShardedActorRef
- [ ] Remove unchecked casts to RecipientRef
- [ ] Add compile-time type validation

### 10. Error Handling
- [ ] Create custom exception hierarchy
- [ ] Add ActorTimeoutException
- [ ] Add ActorNotAvailableException
- [ ] Implement circuit breaker pattern
- [ ] Add error recovery strategies

### 11. Testing Utilities
- [ ] Create TestSpringActorRef for unit testing
- [ ] Create TestSpringShardedActorRef for unit testing
- [ ] Add ActorTestKit integration
- [ ] Create test fixtures and helpers
- [ ] Add behavior verification utilities

### 12. Documentation
- [ ] Write comprehensive JavaDoc for all public APIs
- [ ] Create user guide with examples
- [ ] Add migration guide from raw Pekko
- [ ] Create best practices documentation
- [ ] Add troubleshooting guide

## Implementation Priority

### High Priority (Core functionality, potential breaking changes)
- [ ] Align timeout configuration between classes
- [ ] Add builder pattern for better initialization
- [ ] Implement type-safe generic bounds
- [ ] Add getUnderlying() methods

### Medium Priority (Backward compatible enhancements)
- [ ] Add async tell methods
- [ ] Add retry support with policies
- [ ] Add batch operations
- [ ] Spring configuration support
- [ ] Reactive wrapper methods
- [ ] Basic metrics integration

### Low Priority (Nice to have)
- [ ] Advanced observability features
- [ ] Lifecycle management
- [ ] Testing utilities
- [ ] Circuit breaker pattern
- [ ] Grafana dashboards

## Testing Checklist

- [ ] Unit tests for all new methods
- [ ] Integration tests with Spring Boot context
- [ ] Performance benchmarks for batch operations
- [ ] Timeout behavior verification
- [ ] Retry mechanism testing
- [ ] Metrics accuracy verification
- [ ] Reactive stream backpressure tests
- [ ] Thread safety verification
- [ ] Memory leak tests for long-running operations

## Migration Checklist

- [ ] Document all breaking changes
- [ ] Provide code migration examples
- [ ] Create compatibility layer for smooth transition
- [ ] Update all example projects
- [ ] Create migration script/tool if needed
- [ ] Plan deprecation timeline
- [ ] Update version to indicate breaking changes (if any)