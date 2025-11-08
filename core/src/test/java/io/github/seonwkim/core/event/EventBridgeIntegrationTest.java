package io.github.seonwkim.core.event;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.EnableActorSupport;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Integration test for Spring Events Bridge functionality.
 */
@SpringBootTest(classes = EventBridgeIntegrationTest.TestApp.class)
public class EventBridgeIntegrationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestOrderActor testOrderActor;

    @Autowired
    private EventCaptureListener eventCaptureListener;

    @SpringBootApplication
    @EnableActorSupport
    @ComponentScan(basePackageClasses = EventBridgeIntegrationTest.class)
    static class TestApp {}

    /**
     * Test event representing an order being placed.
     */
    public static class OrderPlacedEvent extends ApplicationEvent {
        private final String orderId;
        private final double amount;

        public OrderPlacedEvent(Object source, String orderId, double amount) {
            super(source);
            this.orderId = orderId;
            this.amount = amount;
        }

        public String getOrderId() {
            return orderId;
        }

        public double getAmount() {
            return amount;
        }
    }

    /**
     * Test event representing an order being created in the actor system.
     */
    public static class OrderCreatedEvent extends ApplicationEvent {
        private final String orderId;

        public OrderCreatedEvent(String orderId) {
            super(orderId);
            this.orderId = orderId;
        }

        public String getOrderId() {
            return orderId;
        }
    }

    /**
     * Test actor that processes orders.
     */
    @Component
    public static class TestOrderActor implements SpringActor<TestOrderActor.Command> {

        public interface Command {}

        public static class CreateOrder extends AskCommand<String> implements Command {
            private final String orderId;
            private final double amount;

            public CreateOrder(String orderId, double amount) {
                this.orderId = orderId;
                this.amount = amount;
            }

            public String getOrderId() {
                return orderId;
            }

            public double getAmount() {
                return amount;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(CreateOrder.class, (context, msg) -> {
                        // Process the order
                        context.getLog().info("Creating order {} with amount {}", msg.getOrderId(), msg.getAmount());

                        // Publish Spring event
                        ctx.publishApplicationEvent(new OrderCreatedEvent(msg.getOrderId()));

                        // Reply to sender
                        msg.reply("Order created: " + msg.getOrderId());
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    /**
     * Event bridge that forwards Spring events to actors.
     */
    @Component
    public static class OrderEventBridge {

        @EventListener
        @SendToActor(value = TestOrderActor.class, actorId = "test-order-actor")
        public TestOrderActor.CreateOrder onOrderPlaced(OrderPlacedEvent event) {
            return new TestOrderActor.CreateOrder(event.getOrderId(), event.getAmount());
        }
    }

    /**
     * Listener that captures events for testing.
     */
    @Component
    public static class EventCaptureListener {
        private final List<OrderCreatedEvent> capturedEvents = Collections.synchronizedList(new ArrayList<>());

        @EventListener
        public void onOrderCreated(OrderCreatedEvent event) {
            capturedEvents.add(event);
        }

        public List<OrderCreatedEvent> getCapturedEvents() {
            return capturedEvents;
        }

        public void clear() {
            capturedEvents.clear();
        }
    }

    @Test
    public void shouldForwardEventToActor() {
        // Clear any previous events
        eventCaptureListener.clear();

        // Publish Spring event
        eventPublisher.publishEvent(new OrderPlacedEvent(this, "order-123", 99.99));

        // Wait for actor to process and publish event back
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertNotNull(eventCaptureListener.getCapturedEvents());
                    assertEquals(1, eventCaptureListener.getCapturedEvents().size());
                    assertEquals("order-123", eventCaptureListener.getCapturedEvents().get(0).getOrderId());
                });
    }

    @Test
    public void shouldPublishEventFromActor() throws Exception {
        // Clear any previous events
        eventCaptureListener.clear();

        // Send message directly to actor
        actorSystem
                .getOrSpawn(TestOrderActor.class, "test-order-actor-2")
                .thenCompose(actorRef -> actorRef.ask(new TestOrderActor.CreateOrder("order-456", 150.00))
                        .withTimeout(Duration.ofSeconds(3))
                        .execute())
                .toCompletableFuture()
                .get();

        // Wait for event to be published
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    List<OrderCreatedEvent> events = eventCaptureListener.getCapturedEvents();
                    assertTrue(
                            events.stream().anyMatch(e -> e.getOrderId().equals("order-456")),
                            "Should have received order-456 event");
                });
    }

    @Test
    public void shouldAutoConfigureEventBridge() {
        // Verify that event bridge beans are created
        assertNotNull(actorSystem, "SpringActorSystem should be autowired");
        assertNotNull(eventPublisher, "ApplicationEventPublisher should be autowired");
        assertNotNull(testOrderActor, "TestOrderActor should be autowired");
    }
}
