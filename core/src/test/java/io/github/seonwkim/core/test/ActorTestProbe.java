package io.github.seonwkim.core.test;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Spring-friendly wrapper around Pekko's TestProbe for testing asynchronous actor responses.
 *
 * <p>This class wraps Pekko TestProbe and provides convenient message expectation helpers. It does
 * NOT reimplement Pekko TestProbe functionality - it simply wraps it with a more convenient API.
 *
 * <p><strong>KEY PRINCIPLE:</strong> Wrap Pekko TestProbe, don't reimplement.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Test
 * public void testAsyncResponse() {
 *     ActorTestProbe<Response> probe = testKit.createProbe();
 *
 *     ActorRef<Command> actor = testKit.spawn(behavior, "test");
 *     actor.tell(new ProcessAsync(probe.ref()));
 *
 *     probe.expectMessageThat(Response.class, response -> {
 *         assertEquals("SUCCESS", response.status());
 *     });
 * }
 * }</pre>
 */
public class ActorTestProbe<T> {

    private final TestProbe<T> pekkoProbe;
    private final Duration defaultTimeout;

    /**
     * Creates a new ActorTestProbe wrapping the given Pekko TestProbe.
     *
     * @param pekkoProbe The Pekko TestProbe to wrap
     */
    public ActorTestProbe(TestProbe<T> pekkoProbe) {
        this(pekkoProbe, Duration.ofSeconds(3));
    }

    /**
     * Creates a new ActorTestProbe with a custom default timeout.
     *
     * @param pekkoProbe The Pekko TestProbe to wrap
     * @param defaultTimeout The default timeout for message expectations
     */
    public ActorTestProbe(TestProbe<T> pekkoProbe, Duration defaultTimeout) {
        if (pekkoProbe == null) {
            throw new IllegalArgumentException("pekkoProbe must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.pekkoProbe = pekkoProbe;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Gets the actor reference for this probe.
     *
     * <p>This reference can be passed to actors to receive messages.
     *
     * @return The actor reference for this probe
     */
    public ActorRef<T> ref() {
        return pekkoProbe.ref();
    }

    /**
     * Receives a message within the default timeout.
     *
     * @return The received message
     */
    public T receiveMessage() {
        return pekkoProbe.receiveMessage(defaultTimeout);
    }

    /**
     * Receives a message within the specified timeout.
     *
     * @param timeout The timeout duration
     * @return The received message
     */
    public T receiveMessage(Duration timeout) {
        return pekkoProbe.receiveMessage(timeout);
    }

    /**
     * Expects a message of the specified class within the default timeout.
     *
     * @param messageClass The expected message class
     * @param <M> The message type
     * @return The received message
     */
    public <M extends T> M expectMessageClass(Class<M> messageClass) {
        return pekkoProbe.expectMessageClass(messageClass, defaultTimeout);
    }

    /**
     * Expects a message of the specified class within the specified timeout.
     *
     * @param messageClass The expected message class
     * @param timeout The timeout duration
     * @param <M> The message type
     * @return The received message
     */
    public <M extends T> M expectMessageClass(Class<M> messageClass, Duration timeout) {
        return pekkoProbe.expectMessageClass(messageClass, timeout);
    }

    /**
     * Expects no message within a short duration (100ms).
     */
    public void expectNoMessage() {
        pekkoProbe.expectNoMessage(Duration.ofMillis(100));
    }

    /**
     * Expects no message within the specified duration.
     *
     * @param duration The duration to wait
     */
    public void expectNoMessage(Duration duration) {
        pekkoProbe.expectNoMessage(duration);
    }

    /**
     * Expects a message matching the given predicate.
     *
     * @param predicate The predicate to test the message
     * @return This probe for chaining
     */
    public ActorTestProbe<T> expectMessageMatching(Predicate<T> predicate) {
        T message = receiveMessage();
        if (!predicate.test(message)) {
            throw new AssertionError("Message did not match predicate: " + message);
        }
        return this;
    }

    /**
     * Expects a message of the specified class and performs assertions on it.
     *
     * @param messageClass The expected message class
     * @param assertion The assertion to perform on the message
     * @param <M> The message type
     * @return This probe for chaining
     */
    public <M extends T> ActorTestProbe<T> expectMessageThat(
            Class<M> messageClass, Consumer<M> assertion) {
        M message = expectMessageClass(messageClass);
        assertion.accept(message);
        return this;
    }

    /**
     * Gets the underlying Pekko TestProbe for advanced usage.
     *
     * @return The underlying Pekko TestProbe
     */
    public TestProbe<T> getPekkoProbe() {
        return pekkoProbe;
    }
}
