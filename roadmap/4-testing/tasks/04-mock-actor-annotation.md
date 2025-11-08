# Task 2.1: @MockActor Annotation

**Priority:** HIGH  
**Estimated Effort:** 3-4 days  
**Phase:** 2 - Mock Actor Support

---

## Overview

Create `@MockActor` annotation to enable easy mocking of SpringActorRef instances in unit tests, integrating with Mockito and Spring Test framework.

## Goals

1. Provide annotation-based actor mocking for unit tests
2. Integrate with Mockito for behavior configuration
3. Work seamlessly with Spring Test (@SpringBootTest, @MockBean)
4. Enable testing of services that use actors without spawning real actors

## Implementation Details

### 1. @MockActor Annotation

Create: `core/src/test/java/io/github/seonwkim/core/test/MockActor.java`

```java
package io.github.seonwkim.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MockActor {
    /**
     * The name of the mock actor (optional).
     */
    String name() default "";
    
    /**
     * Whether to reset the mock after each test.
     */
    boolean reset() default true;
}
```

### 2. MockActorPostProcessor

Create: `core/src/test/java/io/github/seonwkim/core/test/MockActorPostProcessor.java`

```java
public class MockActorPostProcessor implements BeanPostProcessor {
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> clazz = bean.getClass();
        
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(MockActor.class)) {
                MockActor annotation = field.getAnnotation(MockActor.class);
                field.setAccessible(true);
                
                try {
                    // Create mock SpringActorRef
                    Object mock = createMockActorRef(field.getType(), annotation.name());
                    field.set(bean, mock);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to inject mock actor", e);
                }
            }
        }
        
        return bean;
    }
    
    @SuppressWarnings("unchecked")
    private <T> SpringActorRef<T> createMockActorRef(Class<?> fieldType, String name) {
        return Mockito.mock(SpringActorRef.class, name.isEmpty() ? 
            Mockito.withSettings() : 
            Mockito.withSettings().name(name));
    }
}
```

### 3. Integration with ActorTestConfiguration

Update `ActorTestConfiguration` to register the post-processor:

```java
@Configuration
public class ActorTestConfiguration {
    
    // ... existing beans ...
    
    @Bean
    public MockActorPostProcessor mockActorPostProcessor() {
        return new MockActorPostProcessor();
    }
}
```

### 4. MockActorRef Helper (Optional)

Create: `core/src/test/java/io/github/seonwkim/core/test/MockActorRef.java`

```java
public class MockActorRef {
    
    /**
     * Create a mock SpringActorRef with default behavior.
     */
    public static <T> SpringActorRef<T> create() {
        return Mockito.mock(SpringActorRef.class);
    }
    
    /**
     * Create a mock SpringActorRef with a name.
     */
    public static <T> SpringActorRef<T> create(String name) {
        return Mockito.mock(SpringActorRef.class, Mockito.withSettings().name(name));
    }
    
    /**
     * Configure mock to respond to ask() with a specific reply.
     */
    public static <T, R> void whenAsk(
            SpringActorRef<T> mock, 
            Class<?> commandClass, 
            R reply) {
        when(mock.ask(any(commandClass)))
            .thenReturn(CompletableFuture.completedFuture(reply));
    }
    
    /**
     * Configure mock to respond to ask() with a supplier.
     */
    public static <T, R> void whenAsk(
            SpringActorRef<T> mock,
            Class<?> commandClass,
            Supplier<R> replySupplier) {
        when(mock.ask(any(commandClass)))
            .thenAnswer(invocation -> CompletableFuture.completedFuture(replySupplier.get()));
    }
}
```

## Usage Examples

### Example 1: Basic Mock Actor

```java
@SpringBootTest
@EnableActorTesting
public class OrderServiceTest {
    
    @MockActor
    private SpringActorRef<OrderActor.Command> orderActor;
    
    @Autowired
    private OrderService orderService;
    
    @Test
    public void testServiceUsesActor() {
        // Configure mock behavior
        when(orderActor.ask(any(CreateOrder.class)))
            .thenReturn(CompletableFuture.completedFuture(
                new OrderCreated("order-1", 100.0)
            ));
        
        // Test service
        OrderResult result = orderService.createOrder("order-1", 100.0);
        
        // Verify
        assertEquals("SUCCESS", result.status());
        verify(orderActor).tell(any(CreateOrder.class));
    }
}
```

### Example 2: Multiple Mock Actors

```java
@SpringBootTest
@EnableActorTesting
public class WorkflowServiceTest {
    
    @MockActor(name = "orderActor")
    private SpringActorRef<OrderActor.Command> orderActor;
    
    @MockActor(name = "inventoryActor")
    private SpringActorRef<InventoryActor.Command> inventoryActor;
    
    @Autowired
    private WorkflowService workflowService;
    
    @Test
    public void testWorkflowWithMultipleActors() {
        // Configure mocks
        when(orderActor.ask(any(CreateOrder.class)))
            .thenReturn(CompletableFuture.completedFuture(new OrderCreated("order-1", 100.0)));
        
        when(inventoryActor.ask(any(ReserveInventory.class)))
            .thenReturn(CompletableFuture.completedFuture(new InventoryReserved("item-1")));
        
        // Test workflow
        workflowService.processOrder("order-1");
        
        // Verify interactions
        verify(orderActor).tell(any(CreateOrder.class));
        verify(inventoryActor).tell(any(ReserveInventory.class));
    }
}
```

### Example 3: Using Helper Methods

```java
@Test
public void testWithHelperMethods() {
    SpringActorRef<OrderActor.Command> mockActor = MockActorRef.create("order");
    
    MockActorRef.whenAsk(mockActor, CreateOrder.class, 
        new OrderCreated("order-1", 100.0));
    
    // Use in test
    CompletableFuture<OrderCreated> result = mockActor.ask(new CreateOrder("order-1", 100.0));
    assertEquals("order-1", result.join().orderId());
}
```

## Testing

Create comprehensive tests in: `core/src/test/java/io/github/seonwkim/core/test/MockActorTest.java`

Test:
1. @MockActor field injection
2. Mock behavior configuration with Mockito
3. Verification of actor interactions
4. Multiple mocks in single test
5. Named mocks
6. Helper method usage
7. Integration with @SpringBootTest

## Dependencies

Add to `core/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...
    
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
}
```

## Success Criteria

- [ ] @MockActor annotation created
- [ ] MockActorPostProcessor handles field injection
- [ ] Integration with Mockito for behavior configuration
- [ ] Works with @SpringBootTest
- [ ] Helper methods for common mock patterns
- [ ] Multiple mocks supported in single test
- [ ] Tests demonstrate all usage patterns

## Notes

- Use Mockito for all mocking functionality
- Don't create custom mocking framework
- Provide convenience helpers, not reimplementation
- Ensure mocks can be verified like any Mockito mock
- Support both tell() and ask() patterns
- Document limitations of mocking actors
