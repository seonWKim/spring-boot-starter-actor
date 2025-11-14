package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorSpawner;
import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.topic.SpringTopicRef;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.pubsub.Topic;
import org.apache.pekko.cluster.typed.ClusterSingleton;

/**
 * Default implementation of the {@code RootGuardian} interface. This class manages the lifecycle of
 * actors by handling spawn commands.
 */
public class DefaultRootGuardian implements RootGuardian {

    /**
     * Creates a new DefaultRootGuardian behavior with the given actor type registry.
     *
     * @param registry The ActorTypeRegistry to use for creating actor behaviors
     * @return A behavior for the DefaultRootGuardian
     */
    public static Behavior<Command> create(ActorTypeRegistry registry) {
        return Behaviors.setup(ctx -> {
            // Try to get ClusterSingleton if available (cluster mode)
            ClusterSingleton clusterSingleton = null;
            try {
                clusterSingleton = ClusterSingleton.get(ctx.getSystem());
            } catch (Exception e) {
                // Not in cluster mode, clusterSingleton will be null
            }
            return new DefaultRootGuardian(ctx, registry, clusterSingleton).behavior();
        });
    }

    /** The actor context */
    private final ActorContext<Command> ctx;
    /** The actor type registry */
    private final ActorTypeRegistry registry;
    /** The cluster singleton (null in local mode) */
    @Nullable private final ClusterSingleton clusterSingleton;

    /**
     * Creates a new DefaultRootGuardian with the given actor context and actor type registry.
     *
     * @param ctx The actor context
     * @param registry The actor type registry
     * @param clusterSingleton The cluster singleton (null in local mode)
     */
    public DefaultRootGuardian(
            ActorContext<Command> ctx, ActorTypeRegistry registry, @Nullable ClusterSingleton clusterSingleton) {
        this.ctx = ctx;
        this.registry = registry;
        this.clusterSingleton = clusterSingleton;
    }

    /**
     * Creates the behavior for this DefaultRootGuardian. The behavior handles SpawnActor, GetActor, CheckExists,
     * CreateTopic, and GetOrCreateTopic commands.
     *
     * @return A behavior for this DefaultRootGuardian
     */
    private Behavior<Command> behavior() {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
                .onMessage(SpawnActor.class, this::handleSpawnActor)
                .onMessage(GetActor.class, this::handleGetActor)
                .onMessage(CheckExists.class, this::handleCheckExists)
                .onMessage(CreateTopic.class, this::handleCreateTopicRaw)
                .onMessage(GetOrCreateTopic.class, this::handleGetOrCreateTopicRaw)
                .build());
    }

    /**
     * Handles a SpawnActor command by creating a new actor. Each spawn request creates a new actor
     * instance. Users should implement their own caching if they want to reuse actor references.
     *
     * <p>If an actor with the same name already exists, Pekko will throw an InvalidActorNameException.
     * Users should either use unique IDs or implement caching to reuse actor references.
     *
     * <p>If isClusterSingleton is true, spawns the actor as a cluster singleton using Pekko's
     * ClusterSingleton API. The returned reference is a proxy that routes messages to whichever
     * node currently hosts the singleton.
     *
     * @param msg The SpawnActor command
     * @return The same behavior, as this handler doesn't change the behavior
     */
    public Behavior<RootGuardian.Command> handleSpawnActor(SpawnActor msg) {
        String key = buildActorKey(msg.actorClass, msg.actorContext);

        ActorRef<?> ref = ActorSpawner.spawnActor(
                ctx,
                registry,
                msg.actorClass,
                msg.actorContext,
                key,
                msg.supervisorStrategy,
                msg.mailboxConfig,
                msg.dispatcherConfig,
                msg.tagsConfig,
                clusterSingleton,
                msg.isClusterSingleton);

        msg.replyTo.tell(new Spawned<>(ref));
        return Behaviors.same();
    }

    /**
     * Handles a GetActor command by looking up a child actor using the actor context.
     * No caching is used - the lookup is performed directly on the actor context.
     *
     * @param msg The GetActor command
     * @return The same behavior
     */
    public Behavior<RootGuardian.Command> handleGetActor(GetActor msg) {
        ActorRef<?> ref = ActorSpawner.getActor(ctx, msg.actorClass, msg.actorContext.actorId());
        msg.replyTo.tell(new GetActorResponse<>(ref));
        return Behaviors.same();
    }

    /**
     * Handles a CheckExists command by checking if a child actor exists using the actor context.
     * No caching is used - the lookup is performed directly on the actor context.
     *
     * @param msg The CheckExists command
     * @return The same behavior
     */
    public Behavior<RootGuardian.Command> handleCheckExists(CheckExists msg) {
        boolean exists = ActorSpawner.actorExists(ctx, msg.actorClass, msg.actorContext.actorId());
        msg.replyTo.tell(new ExistsResponse(exists));
        return Behaviors.same();
    }

    /**
     * Handles a CreateTopic command by creating a new Pekko Topic actor (raw handler for type erasure).
     */
    @SuppressWarnings("unchecked")
    public Behavior<RootGuardian.Command> handleCreateTopicRaw(CreateTopic<?> msg) {
        return handleCreateTopic((CreateTopic<Object>) msg);
    }

    /**
     * Handles a CreateTopic command by creating a new Pekko Topic actor.
     * The topic identity is based on both the message type and topic name, following Pekko's
     * recommendation. The actor name includes both to ensure uniqueness.
     *
     * @param msg The CreateTopic command
     * @return The same behavior
     */
    @SuppressWarnings("unchecked")
    private <T> Behavior<RootGuardian.Command> handleCreateTopic(CreateTopic<T> msg) {
        // Build actor name that includes both topic name and message type
        // This ensures topics with same name but different types are distinct
        String actorName = buildTopicActorName(msg.topicName, msg.messageType);

        ActorRef<Topic.Command<T>> topicActor =
                ctx.spawn(Topic.create(msg.messageType, msg.topicName), actorName);
        SpringTopicRef<T> topicRef = new SpringTopicRef<>(topicActor, msg.topicName);

        // Cast is safe because we're creating the topic with the correct type
        msg.replyTo.tell(new TopicCreated<>(topicRef));
        return Behaviors.same();
    }

    /**
     * Handles a GetOrCreateTopic command (raw handler for type erasure).
     */
    @SuppressWarnings("unchecked")
    public Behavior<RootGuardian.Command> handleGetOrCreateTopicRaw(GetOrCreateTopic<?> msg) {
        return handleGetOrCreateTopic((GetOrCreateTopic<Object>) msg);
    }

    /**
     * Handles a GetOrCreateTopic command by getting an existing topic or creating a new one.
     * This operation is idempotent - multiple calls with the same name and type will return the same topic.
     * The topic identity is based on both the message type and topic name.
     *
     * @param msg The GetOrCreateTopic command
     * @return The same behavior
     */
    @SuppressWarnings("unchecked")
    private <T> Behavior<RootGuardian.Command> handleGetOrCreateTopic(GetOrCreateTopic<T> msg) {
        // Try to get existing topic using full identity (name + type)
        SpringTopicRef<Object> topicRef;

        String actorName = buildTopicActorName(msg.topicName, msg.messageType);
        ActorRef<?> existingRef = ctx.getChild(actorName).orElse(null);

        if (existingRef != null) {
            topicRef = new SpringTopicRef<>((ActorRef<Topic.Command<Object>>) existingRef, msg.topicName);
        } else {
            // Create new topic with full identity
            ActorRef<Topic.Command<Object>> topicActor =
                    ctx.spawn(Topic.create((Class<Object>) msg.messageType, msg.topicName), actorName);
            topicRef = new SpringTopicRef<>(topicActor, msg.topicName);
        }

        ((ActorRef<TopicCreated<Object>>) (ActorRef<?>) msg.replyTo).tell(new TopicCreated<>(topicRef));
        return Behaviors.same();
    }

    /**
     * Builds an actor name for a topic that includes both the topic name and message type.
     * This ensures that topics with the same name but different message types are distinct,
     * following Pekko's topic identity specification.
     *
     * @param topicName The topic name
     * @param messageType The message type class
     * @return A unique actor name combining both topic name and message type
     */
    private String buildTopicActorName(String topicName, Class<?> messageType) {
        return "topic-" + topicName + "-" + messageType.getName().replace(".", "_");
    }

    private String buildActorKey(Class<?> actorClass, SpringActorContext actorContext) {
        return io.github.seonwkim.core.ActorSpawner.buildActorName(actorClass, actorContext.actorId());
    }
}
