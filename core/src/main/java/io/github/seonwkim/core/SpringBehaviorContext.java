package io.github.seonwkim.core;

import io.github.seonwkim.core.pubsub.SpringTopicRef;
import io.github.seonwkim.core.pubsub.TopicSpawner;
import org.apache.pekko.actor.ActorPath;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Spring-enhanced wrapper around Pekko's ActorContext used during behavior creation.
 *
 * <p>This class provides a bridge between Pekko's actor system and Spring Boot Starter Actor,
 * offering convenient methods for common actor operations including pub/sub topic creation.
 *
 * <p>Key features:
 * <ul>
 *   <li>Create pub/sub topics using {@link #createTopic(Class, String)}
 *   <li>Full access to underlying Pekko ActorContext via {@link #getUnderlying()}
 *   <li>Convenient delegate methods for common ActorContext operations
 * </ul>
 *
 * <p>Example usage in behavior creation:
 * <pre>
 * {@code
 * @Override
 * public SpringActorBehavior<Command> create(MyContext myContext) {
 *     return SpringActorBehavior.builder(Command.class, myContext)
 *         .withState(ctx -> {
 *             // Create a topic directly from the context
 *             SpringTopicRef<MyMessage> topic = ctx.createTopic(MyMessage.class, "my-topic");
 *             return new MyBehavior(ctx, topic);
 *         })
 *         .onMessage(DoWork.class, MyBehavior::handleWork)
 *         .build();
 * }
 * }
 * </pre>
 *
 * @param <T> The message type that the actor handles
 * @see SpringActorBehavior
 * @see SpringTopicRef
 */
public final class SpringBehaviorContext<T> {

    private final ActorContext<T> underlying;

    /**
     * Creates a new SpringBehaviorContext wrapping the given ActorContext.
     *
     * @param underlying The Pekko ActorContext to wrap
     */
    public SpringBehaviorContext(ActorContext<T> underlying) {
        this.underlying = underlying;
    }

    /**
     * Creates a pub/sub topic with the given message type and name.
     *
     * <p>This method spawns a Pekko Topic actor as a child of the current actor.
     * Topics enable distributed publish-subscribe messaging across the cluster.
     *
     * <p>Example:
     * <pre>
     * {@code
     * SpringTopicRef<ChatMessage> chatTopic = ctx.createTopic(ChatMessage.class, "chat-room-1");
     * chatTopic.publish(new ChatMessage("Hello!"));
     * }
     * </pre>
     *
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the created topic
     * @see SpringTopicRef
     */
    public <M> SpringTopicRef<M> createTopic(Class<M> messageType, String topicName) {
        return TopicSpawner.createTopic(underlying, messageType, topicName);
    }

    /**
     * Gets a reference to an existing topic, or creates it if it doesn't exist.
     * This provides idempotent topic creation semantics.
     *
     * <p>Note: This creates an actor-owned topic that will be stopped when this actor stops.
     *
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this topic
     * @param <M> The message type
     * @return A reference to the topic (existing or newly created)
     * @see SpringTopicRef
     */
    public <M> SpringTopicRef<M> getOrCreateTopic(Class<M> messageType, String topicName) {
        return TopicSpawner.getOrCreateTopic(underlying, messageType, topicName);
    }

    /**
     * Gets a reference to an existing system-level topic, or creates it if it doesn't exist.
     *
     * <p>System-level topics persist independently of any actor's lifecycle and are ideal for
     * scenarios where the topic should outlive individual actor instances, such as:
     * <ul>
     *   <li>Chat rooms that persist across room actor passivations
     *   <li>Event buses shared across the entire system
     *   <li>Cross-cluster communication channels
     * </ul>
     *
     * <p><b>Important:</b> System-level topics cannot be stopped programmatically. They exist
     * for the lifetime of the ActorSystem. Choose actor-owned topics ({@link #createTopic})
     * if you need explicit lifecycle control.
     *
     * <p>Example:
     * <pre>
     * {@code
     * // Create a system-level topic that persists across actor restarts
     * SpringTopicRef<ChatMessage> chatTopic =
     *     ctx.getOrCreateSystemTopic(ChatMessage.class, "chat-room-" + roomId);
     * }
     * </pre>
     *
     * @param messageType The type of messages this topic will handle
     * @param topicName The unique name for this system-level topic
     * @param <M> The message type
     * @return A reference to the system-level topic (existing or newly created)
     * @throws IllegalStateException if a topic with this name already exists
     * @see SpringTopicRef
     * @see #createTopic
     */
    public <M> SpringTopicRef<M> getOrCreateSystemTopic(Class<M> messageType, String topicName) {
        return TopicSpawner.getOrCreateTopic(underlying.getSystem(), messageType, topicName);
    }

    /**
     * Returns the underlying Pekko ActorContext.
     *
     * <p>Use this when you need direct access to Pekko-specific functionality not exposed
     * through this wrapper's convenience methods.
     *
     * @return The underlying ActorContext
     */
    public ActorContext<T> getUnderlying() {
        return underlying;
    }

    // Convenience delegate methods for common ActorContext operations

    /**
     * Returns the ActorRef for this actor.
     *
     * @return This actor's reference
     */
    public SpringActorRef<T> getSelf() {
        return new SpringActorRef<>(underlying.getSystem().scheduler(), underlying.getSelf());
    }

    /**
     * Returns the logger for this actor.
     *
     * @return The SLF4J logger instance
     */
    public Logger getLog() {
        return underlying.getLog();
    }

    /**
     * Returns the scheduler for this actor system.
     *
     * @return The scheduler
     */
    public Scheduler getScheduler() {
        return underlying.getSystem().scheduler();
    }

    /**
     * Spawns a child actor with the given behavior and name.
     *
     * @param behavior The behavior for the child actor
     * @param name The name for the child actor
     * @param <M> The message type the child handles
     * @return Reference to the spawned child actor
     */
    public <M> ActorRef<M> spawn(Behavior<M> behavior, String name) {
        return underlying.spawn(behavior, name);
    }

    // TODO: update docs
    public ActorPath path() {
        return underlying.getSelf().path();
    }

    // TODO: update docs
    public Optional<ActorRef<Void>> getChild(String name) {
        return underlying.getChild(name);
    }

    // TODO: update docs
    public List<ActorRef<Void>> getChildren() {
        return underlying.getChildren();
    }

    // TODO: update docs and check signature is approprate
    public void stop(ActorRef<?> child) {
        underlying.stop(child);
    }
}
