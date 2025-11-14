package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorSpawner;
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
     * Creates a new DefaultRootGuardian behavior using the static ActorTypeRegistry.
     *
     * @return A behavior for the DefaultRootGuardian
     */
    public static Behavior<Command> create() {
        return Behaviors.setup(ctx -> {
            // Try to get ClusterSingleton if available (cluster mode)
            ClusterSingleton clusterSingleton = null;
            try {
                clusterSingleton = ClusterSingleton.get(ctx.getSystem());
            } catch (Exception e) {
                // Not in cluster mode, clusterSingleton will be null
            }
            return new DefaultRootGuardian(ctx, clusterSingleton).behavior();
        });
    }

    /** The actor context */
    private final ActorContext<Command> ctx;
    /** The cluster singleton (null in local mode) */
    @Nullable private final ClusterSingleton clusterSingleton;

    /**
     * Creates a new DefaultRootGuardian with the given actor context.
     * Uses the static ActorTypeRegistry for actor creation.
     *
     * @param ctx The actor context
     * @param clusterSingleton The cluster singleton (null in local mode)
     */
    public DefaultRootGuardian(ActorContext<Command> ctx, @Nullable ClusterSingleton clusterSingleton) {
        this.ctx = ctx;
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
     * <p>If a topic with the same name and message type already exists, this will return
     * an error response. Use GetOrCreateTopic for idempotent topic creation.
     *
     * @param msg The CreateTopic command
     * @return The same behavior
     */
    @SuppressWarnings("unchecked")
    private <T> Behavior<RootGuardian.Command> handleCreateTopic(CreateTopic<T> msg) {
        // Build actor name that includes both topic name and message type
        // This ensures topics with same name but different types are distinct
        String actorName = buildTopicActorName(msg.topicName, msg.messageType);

        // Check if topic already exists
        if (ctx.getChild(actorName).isPresent()) {
            msg.replyTo.tell(TopicCreated.alreadyExists(String.format(
                    "Topic '%s' with message type '%s' already exists", msg.topicName, msg.messageType.getName())));
            return Behaviors.same();
        }

        ActorRef<Topic.Command<T>> topicActor = ctx.spawn(Topic.create(msg.messageType, msg.topicName), actorName);
        SpringTopicRef<T> topicRef = new SpringTopicRef<>(topicActor, msg.topicName);

        msg.replyTo.tell(TopicCreated.success(topicRef));
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
    private <T> Behavior<RootGuardian.Command> handleGetOrCreateTopic(GetOrCreateTopic<T> msg) {
        String actorName = buildTopicActorName(msg.topicName, msg.messageType);
        ActorRef<?> existingRef = ctx.getChild(actorName).orElse(null);

        SpringTopicRef<T> topicRef;
        if (existingRef != null) {
            // Reuse existing topic - cast is type-safe due to actor name uniqueness
            topicRef = castToTopicRef(existingRef, msg.topicName);
        } else {
            // Create new topic with full identity
            topicRef = createNewTopic(msg.topicName, msg.messageType, actorName);
        }

        msg.replyTo.tell(TopicCreated.success(topicRef));
        return Behaviors.same();
    }

    /**
     * Safely casts an existing actor reference to a SpringTopicRef with the correct type.
     *
     * <p><b>TYPE SAFETY INVARIANT:</b> This cast is safe because of our actor naming strategy.
     * The actor name is constructed as: {@code "topic-" + topicName + "-" + messageType.getName()}.
     * This ensures that:
     * <ul>
     *   <li>Each combination of (topicName, messageType) maps to a unique actor name</li>
     *   <li>When we look up an actor by name, we know exactly what type it was created with</li>
     *   <li>Two topics with the same name but different types have different actor names</li>
     * </ul>
     *
     * <p>Therefore, when we retrieve an actor using {@code buildTopicActorName(name, type)} and
     * find an existing actor, we are guaranteed that it was created with the same message type.
     *
     * @param actorRef The existing actor reference from getChild()
     * @param topicName The topic name (for debugging and reference creation)
     * @param <T> The message type
     * @return A type-safe SpringTopicRef
     */
    @SuppressWarnings("unchecked")
    private <T> SpringTopicRef<T> castToTopicRef(ActorRef<?> actorRef, String topicName) {
        // The cast is safe because:
        // 1. We looked up the actor using buildTopicActorName(topicName, messageType)
        // 2. That actor name encodes both the topic name AND the message type
        // 3. Only topics created with messageType would have this actor name
        // 4. Therefore, the actor must be ActorRef<Topic.Command<T>>
        ActorRef<Topic.Command<T>> typedRef = (ActorRef<Topic.Command<T>>) actorRef;
        return new SpringTopicRef<>(typedRef, topicName);
    }

    /**
     * Creates a new topic actor with the specified name and message type.
     *
     * @param topicName The topic name
     * @param messageType The message type class
     * @param actorName The unique actor name (includes both topic name and message type)
     * @param <T> The message type
     * @return A SpringTopicRef for the newly created topic
     */
    private <T> SpringTopicRef<T> createNewTopic(String topicName, Class<T> messageType, String actorName) {
        ActorRef<Topic.Command<T>> topicActor = ctx.spawn(Topic.create(messageType, topicName), actorName);
        return new SpringTopicRef<>(topicActor, topicName);
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
