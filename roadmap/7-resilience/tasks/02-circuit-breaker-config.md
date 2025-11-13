# Task 1.2: Circuit Breaker Spring Boot Configuration

**Priority:** HIGH
**Estimated Effort:** 3-4 days
**Dependencies:** Task 1.1 (Circuit Breaker Integration)
**Assignee:** AI Agent

---

## Objective

Implement Spring Boot YAML configuration support for circuit breakers, enabling per-actor configuration through application properties.

---

## Requirements

### 1. YAML Configuration Structure

```yaml
spring:
  actor:
    circuit-breaker:
      enabled: true
      defaults:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 10s
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
      instances:
        order-processing:
          failure-rate-threshold: 60
          wait-duration-in-open-state: 60s
        payment-gateway:
          failure-rate-threshold: 30
          minimum-number-of-calls: 10

# Alternative: Use Resilience4j native configuration
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
    instances:
      order-processing:
        base-config: default
        failure-rate-threshold: 60
```

### 2. Auto-Configuration Classes

Create Spring Boot auto-configuration for seamless integration:
- `ActorCircuitBreakerAutoConfiguration`
- `ActorCircuitBreakerProperties`
- Configuration conditional on Resilience4j presence

### 3. Configuration Binding

Support both:
- Spring Actor native configuration (`spring.actor.circuit-breaker.*`)
- Resilience4j native configuration (`resilience4j.circuitbreaker.*`)

---

## Implementation Tasks

### Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerProperties.java`**
   - Configuration properties class
   - Default values
   - Per-instance overrides

2. **`core/src/main/java/io/github/seonwkim/core/circuitbreaker/ActorCircuitBreakerAutoConfiguration.java`**
   - Auto-configuration class
   - Conditional on Resilience4j
   - Bean registration

3. **`core/src/main/resources/META-INF/spring.factories` or `spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`**
   - Register auto-configuration

---

## Configuration Properties Structure

```java
@ConfigurationProperties(prefix = "spring.actor.circuit-breaker")
public class ActorCircuitBreakerProperties {
    private boolean enabled = true;
    private DefaultConfig defaults = new DefaultConfig();
    private Map<String, InstanceConfig> instances = new HashMap<>();
    
    public static class DefaultConfig {
        private int failureRateThreshold = 50;
        private int slowCallRateThreshold = 100;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(10);
        // ... other properties
    }
    
    public static class InstanceConfig {
        // Override properties for specific circuit breakers
    }
}
```

---

## Integration with Resilience4j

```java
@Configuration
@ConditionalOnClass(CircuitBreaker.class)
@EnableConfigurationProperties(ActorCircuitBreakerProperties.class)
public class ActorCircuitBreakerAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            ActorCircuitBreakerProperties properties) {
        
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.getDefaults().getFailureRateThreshold())
            .slowCallRateThreshold(properties.getDefaults().getSlowCallRateThreshold())
            .slowCallDurationThreshold(properties.getDefaults().getSlowCallDurationThreshold())
            // ... configure other properties
            .build();
            
        return CircuitBreakerRegistry.of(defaultConfig);
    }
    
    @Bean
    public ActorCircuitBreakerManager actorCircuitBreakerManager(
            CircuitBreakerRegistry registry,
            ActorCircuitBreakerProperties properties) {
        
        return new ActorCircuitBreakerManager(registry, properties);
    }
}
```

---

## Example Usage

### YAML Configuration
```yaml
spring:
  actor:
    circuit-breaker:
      enabled: true
      defaults:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      instances:
        payment-service:
          failure-rate-threshold: 30
          wait-duration-in-open-state: 60s
```

### Actor Code
```java
@Component
public class PaymentActor implements SpringActor<Command> {
    
    @Override
    public SpringActorBehavior<Command> create(SpringActorContext ctx) {
        return SpringActorBehavior.builder(Command.class, ctx)
            .withCircuitBreaker("payment-service")  // Uses YAML config
            .onMessage(ProcessPayment.class, this::handlePayment)
            .build();
    }
}
```

---

## Testing Requirements

### Unit Tests
```java
@SpringBootTest
public class CircuitBreakerConfigTest {
    
    @Test
    public void testDefaultConfiguration() {
        // Verify default values are applied
    }
    
    @Test
    public void testInstanceConfiguration() {
        // Verify per-instance overrides work
    }
    
    @Test
    public void testConfigurationDisabled() {
        // Verify circuit breakers can be disabled
    }
}
```

---

## Acceptance Criteria

- [ ] YAML configuration for circuit breakers works
- [ ] Default configuration applied to all circuit breakers
- [ ] Per-instance configuration overrides defaults
- [ ] Auto-configuration only activates when Resilience4j is present
- [ ] Configuration can be disabled via `enabled: false`
- [ ] Documentation with configuration examples
- [ ] Tests for all configuration scenarios

---

## Documentation

Update:
- Configuration guide in mkdocs
- README.md with YAML examples
- JavaDoc on configuration classes
