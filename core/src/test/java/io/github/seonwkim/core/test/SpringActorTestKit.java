package io.github.seonwkim.core.test;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Spring-friendly wrapper around Pekko's ActorTestKit for testing actors.
 *
 * <p>This class wraps Pekko ActorTestKit and provides a fluent, Spring Boot-friendly API for
 * actor testing. It does NOT reimplement Pekko TestKit core functionality - it simply wraps it.
 *
 * <p><strong>KEY PRINCIPLE:</strong> Wrap Pekko TestKit, don't reimplement.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @SpringBootTest
 * @EnableActorTesting
 * public class OrderActorTest {
 *
 *     @Autowired
 *     private SpringActorTestKit testKit;
 *
 *     @Test
 *     public void testOrderCreation() {
 *         // Fluent API
 *         testKit.forActor(OrderActor.class)
 *             .withId("test-order")
 *             .spawn()
 *             .send(new CreateOrder("order-1", 100.0))
 *             .expectReply(OrderCreated.class);
 *     }
 *
 *     @Test
 *     public void testWithProbe() {
 *         ActorTestProbe<Response> probe = testKit.createProbe();
 *         ActorRef<Command> actor = testKit.spawn(behavior, "test");
 *         actor.tell(new ProcessAsync(probe.ref()));
 *         Response response = probe.expectMessageClass(Response.class);
 *     }
 * }
 * }</pre>
 */
public class SpringActorTestKit implements AutoCloseable {

    private final ActorTestKit pekkoTestKit;

    /** Creates a new SpringActorTestKit with a default Pekko ActorTestKit. */
    public SpringActorTestKit() {
        this.pekkoTestKit = ActorTestKit.create();
    }

    /**
     * Creates a new SpringActorTestKit with the given Pekko ActorTestKit.
     *
     * @param pekkoTestKit The Pekko ActorTestKit to wrap
     */
    public SpringActorTestKit(ActorTestKit pekkoTestKit) {
        if (pekkoTestKit == null) {
            throw new IllegalArgumentException("pekkoTestKit must not be null");
        }
        this.pekkoTestKit = pekkoTestKit;
    }

    /**
     * Spawns an actor with the given behavior and name.
     *
     * <p>This is a direct delegation to Pekko's TestKit spawn method.
     *
     * @param behavior The behavior of the actor
     * @param name The name of the actor
     * @param <T> The message type the actor handles
     * @return An ActorRef to the spawned actor
     */
    public <T> ActorRef<T> spawn(Behavior<T> behavior, String name) {
        return pekkoTestKit.spawn(behavior, name);
    }

    /**
     * Spawns an actor with the given behavior and an auto-generated name.
     *
     * @param behavior The behavior of the actor
     * @param <T> The message type the actor handles
     * @return An ActorRef to the spawned actor
     */
    public <T> ActorRef<T> spawn(Behavior<T> behavior) {
        return pekkoTestKit.spawn(behavior);
    }

    /**
     * Creates a test probe for the given message type.
     *
     * <p>The probe can be used to verify messages sent by actors under test.
     *
     * @param <T> The message type
     * @return An ActorTestProbe wrapping Pekko's TestProbe
     */
    public <T> ActorTestProbe<T> createProbe() {
        return new ActorTestProbe<>(pekkoTestKit.createTestProbe());
    }

    /**
     * Creates a test probe for the given message type with a name.
     *
     * @param name The name of the probe
     * @param <T> The message type
     * @return An ActorTestProbe wrapping Pekko's TestProbe
     */
    public <T> ActorTestProbe<T> createProbe(String name) {
        return new ActorTestProbe<>(pekkoTestKit.createTestProbe(name));
    }

    /**
     * Creates a test probe for the given message class.
     *
     * @param messageClass The message class
     * @param <T> The message type
     * @return An ActorTestProbe wrapping Pekko's TestProbe
     */
    public <T> ActorTestProbe<T> createProbe(Class<T> messageClass) {
        return new ActorTestProbe<>(pekkoTestKit.createTestProbe(messageClass));
    }

    /**
     * Creates a stopped actor behavior for testing.
     *
     * @param <T> The message type
     * @return A stopped behavior
     */
    public <T> Behavior<T> createStopped() {
        return Behaviors.stopped();
    }

    /**
     * Gets the underlying Pekko ActorTestKit for advanced usage.
     *
     * <p>This provides an escape hatch when you need to use Pekko TestKit functionality that is
     * not wrapped by this class.
     *
     * @return The underlying Pekko ActorTestKit
     */
    public ActorTestKit getPekkoTestKit() {
        return pekkoTestKit;
    }

    /**
     * Shuts down the test kit and the actor system.
     *
     * <p>This method is automatically called when the Spring context is destroyed. You typically
     * don't need to call it manually.
     */
    @Override
    public void close() {
        pekkoTestKit.shutdownTestKit();
    }
}
