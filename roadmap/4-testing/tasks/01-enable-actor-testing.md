# Task 1.1: @EnableActorTesting Annotation

**Priority:** HIGH  
**Estimated Effort:** 3-4 days  
**Phase:** 1 - Spring Boot TestKit Wrapper

---

## Overview

Create a `@EnableActorTesting` annotation that auto-configures testing support in Spring Boot test contexts. This annotation should enable the `SpringActorTestKit` bean and related testing infrastructure.

## Goals

1. Provide a simple annotation to enable actor testing in @SpringBootTest
2. Auto-configure Pekko ActorTestKit wrapped by SpringActorTestKit
3. Ensure proper lifecycle management (setup/teardown)
4. Follow Spring Boot auto-configuration patterns

## Implementation Details

### 1. @EnableActorTesting Annotation

Create annotation in: `core/src/test/java/io/github/seonwkim/core/test/EnableActorTesting.java`

```java
package io.github.seonwkim.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ActorTestConfiguration.class)
public @interface EnableActorTesting {}
```

### 2. ActorTestConfiguration Class

Create configuration in: `core/src/test/java/io/github/seonwkim/core/test/ActorTestConfiguration.java`

```java
@Configuration
public class ActorTestConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(SpringActorTestKit.class)
    public SpringActorTestKit springActorTestKit() {
        return new SpringActorTestKit();
    }
    
    @Bean
    public ActorTestKitLifecycle actorTestKitLifecycle(SpringActorTestKit testKit) {
        return new ActorTestKitLifecycle(testKit);
    }
}
```

### 3. SpringActorTestKit Class

Create the main test kit wrapper in: `core/src/test/java/io/github/seonwkim/core/test/SpringActorTestKit.java`

**KEY PRINCIPLE: Wrap Pekko ActorTestKit, don't reimplement**

```java
public class SpringActorTestKit implements AutoCloseable {
    
    private final ActorTestKit pekkoTestKit;
    
    public SpringActorTestKit() {
        this.pekkoTestKit = ActorTestKit.create();
    }
    
    // Delegate to Pekko TestKit
    public <T> ActorRef<T> spawn(Behavior<T> behavior, String name) {
        return pekkoTestKit.spawn(behavior, name);
    }
    
    // Fluent API entry point
    public <T> ActorTestBuilder<T> forActor(Class<? extends SpringActor<T>> actorClass) {
        return new ActorTestBuilder<>(this, actorClass);
    }
    
    // Direct access to Pekko TestKit if needed
    public ActorTestKit getPekkoTestKit() {
        return pekkoTestKit;
    }
    
    @Override
    public void close() {
        pekkoTestKit.shutdownTestKit();
    }
}
```

### 4. Lifecycle Management

Create: `core/src/test/java/io/github/seonwkim/core/test/ActorTestKitLifecycle.java`

```java
public class ActorTestKitLifecycle implements DisposableBean {
    
    private final SpringActorTestKit testKit;
    
    public ActorTestKitLifecycle(SpringActorTestKit testKit) {
        this.testKit = testKit;
    }
    
    @Override
    public void destroy() {
        testKit.close();
    }
}
```

## Usage Example

```java
@SpringBootTest
@EnableActorTesting
public class OrderActorTest {
    
    @Autowired
    private SpringActorTestKit testKit;
    
    @Test
    public void testOrderCreation() {
        // Fluent API
        testKit.forActor(OrderActor.class)
            .withId("test-order")
            .spawn()
            .send(new CreateOrder("order-1", 100.0))
            .expectReply(OrderCreated.class);
    }
}
```

## Testing

Create test: `core/src/test/java/io/github/seonwkim/core/test/EnableActorTestingTest.java`

Test that:
1. @EnableActorTesting enables SpringActorTestKit bean
2. SpringActorTestKit properly wraps Pekko ActorTestKit
3. Lifecycle management works correctly
4. Multiple test classes can use the same configuration

## Dependencies

No new dependencies required. Use existing:
- `org.apache.pekko:pekko-actor-testkit-typed_3` (already in testImplementation)
- Spring Boot Test framework

## Success Criteria

- [ ] @EnableActorTesting annotation created
- [ ] ActorTestConfiguration auto-configures SpringActorTestKit
- [ ] SpringActorTestKit wraps Pekko ActorTestKit (doesn't reimplement)
- [ ] Proper lifecycle management (setup/teardown)
- [ ] Works with @SpringBootTest
- [ ] Tests pass demonstrating functionality

## Notes

- Keep it simple - just wrap, don't reimplement
- Follow existing patterns from @EnableActorSupport
- Ensure thread-safety for parallel test execution
- Document that this is for testing only (test scope)
