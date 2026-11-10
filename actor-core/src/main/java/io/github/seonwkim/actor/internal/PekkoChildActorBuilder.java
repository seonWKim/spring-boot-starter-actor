package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.MdcConfig;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SpringChildActorBuilder;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

/** Pekko-backed {@link SpringChildActorBuilder}. */
public final class PekkoChildActorBuilder<P, C> implements SpringChildActorBuilder<P, C> {
    private static final Logger log = LoggerFactory.getLogger(PekkoChildActorBuilder.class);

    private final ActorRef<P> parentRef;
    private final ActorSystem<?> system;
    private final Class<? extends SpringActorWithContext<C, ?>> childActorClass;
    private final Duration defaultTimeout;

    @Nullable private String childId;

    @Nullable private SpringActorContext childContext;

    @Nullable private SupervisionStrategy supervisionStrategy;

    @Nullable private MailboxConfig mailboxConfig;

    @Nullable private DispatcherConfig dispatcherConfig;

    @Nullable private TagsConfig tagsConfig;

    private MdcConfig mdcConfig = MdcConfig.empty();
    private Duration timeout;

    /**
     * Creates a new SpringChildActorBuilder.
     *
     * @param parentRef The parent actor reference
     * @param system The actor system, for asks and tracing
     * @param childActorClass The child actor class
     * @param defaultTimeout The default timeout for operations
     */
    public PekkoChildActorBuilder(
            ActorRef<P> parentRef,
            ActorSystem<?> system,
            Class<? extends SpringActorWithContext<C, ?>> childActorClass,
            Duration defaultTimeout) {
        if (parentRef == null) {
            throw new IllegalArgumentException("parentRef must not be null");
        }
        if (system == null) {
            throw new IllegalArgumentException("system must not be null");
        }
        if (childActorClass == null) {
            throw new IllegalArgumentException("childActorClass must not be null");
        }
        this.parentRef = parentRef;
        this.system = system;
        this.childActorClass = childActorClass;
        this.defaultTimeout = defaultTimeout;
        this.timeout = defaultTimeout;
    }

    @Override
    public SpringChildActorBuilder<P, C> withId(String childId) {
        if (childId == null || ObjectUtils.isEmpty(childId)) {
            throw new IllegalArgumentException("childId must not be null or empty");
        }
        this.childId = childId;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withContext(SpringActorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.childContext = context;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withSupervisionStrategy(SupervisionStrategy strategy) {
        this.supervisionStrategy = strategy;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withMailbox(MailboxConfig mailboxConfig) {
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("mailboxConfig must not be null");
        }
        this.mailboxConfig = mailboxConfig;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withDispatcher(DispatcherConfig dispatcherConfig) {
        if (dispatcherConfig == null) {
            throw new IllegalArgumentException("dispatcherConfig must not be null");
        }
        this.dispatcherConfig = dispatcherConfig;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withTags(TagsConfig tagsConfig) {
        if (tagsConfig == null) {
            throw new IllegalArgumentException("tagsConfig must not be null");
        }
        this.tagsConfig = tagsConfig;
        return this;
    }

    @Override
    public SpringChildActorBuilder<P, C> withMdc(MdcConfig mdcConfig) {
        if (mdcConfig == null) {
            throw new IllegalArgumentException("mdcConfig must not be null");
        }
        this.mdcConfig = mdcConfig;
        return this;
    }

    @Override
    public CompletionStage<SpringActorHandle<C>> spawn() {
        return spawn(false);
    }

    @Override
    public SpringActorHandle<C> spawnAndWait() {
        return spawn().toCompletableFuture().join();
    }

    @Override
    public CompletionStage<SpringActorHandle<C>> getOrSpawn() {
        // The parent checks for a running child and spawns in one message, so concurrent callers
        // get the same child.
        return spawn(true);
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<SpringActorHandle<C>> spawn(boolean returnExisting) {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set before spawning. "
                        + "Call withId(\"your-child-id\") or withContext(context) on the builder. "
                        + "Child actor class: "
                        + childActorClass.getName());
            }
            IllegalArgumentException mismatch = ActorSpawner.defaultContextMismatch(childActorClass);
            if (mismatch != null) {
                return CompletableFuture.failedFuture(mismatch);
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        // Apply MDC configuration to the child context
        childContext.setMdcConfig(mdcConfig);

        // Cast parent ref to Object to send framework command
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        final SpringActorContext context = childContext;
        final SupervisionStrategy strategy = supervisionStrategy;
        final MailboxConfig mailbox = mailboxConfig;
        final DispatcherConfig dispatcher = dispatcherConfig;
        final TagsConfig tags = tagsConfig;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.SpawnChildResponse<C>> replyTo) ->
                                new FrameworkCommands.SpawnChild<>(
                                        childActorClass, context, strategy, mailbox, dispatcher, tags, replyTo),
                        timeout,
                        system.scheduler())
                .thenApply(response -> {
                    if (response.success && response.childRef != null) {
                        return new PekkoActorHandle<>(system, response.childRef, defaultTimeout);
                    }
                    if (response.alreadyExists() && response.childRef != null) {
                        if (!returnExisting) {
                            log.warn(
                                    "spawn() found child {} with id '{}' already running and returned it. This is "
                                            + "deprecated: use getOrSpawn() to reuse a running child. In 2.0, spawn() "
                                            + "fails when the id is taken, like a top-level spawn().",
                                    childActorClass.getName(),
                                    context.actorId());
                        }
                        return new PekkoActorHandle<>(system, response.childRef, defaultTimeout);
                    }
                    throw new RuntimeException("Failed to spawn child actor "
                            + childActorClass.getName()
                            + " with id '"
                            + context.actorId()
                            + "': "
                            + response.message);
                });
    }

    @Override
    @Deprecated
    @SuppressWarnings("unchecked")
    public CompletionStage<SpringActorHandle<C>> get() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        final SpringActorContext context = childContext;
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, context.actorId(), replyTo),
                        timeout,
                        system.scheduler())
                .thenApply(response -> {
                    if (response.found && response.childRef != null) {
                        return new PekkoActorHandle<>(system, response.childRef, defaultTimeout);
                    } else {
                        return null;
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Boolean> exists() {
        if (childContext == null) {
            if (childId == null) {
                throw new IllegalStateException("Either childId or childContext must be set");
            }
            childContext = new DefaultSpringActorContext(childId);
        }

        final SpringActorContext context = childContext;
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.ExistsChildResponse> replyTo) ->
                                new FrameworkCommands.ExistsChild<>(childActorClass, context.actorId(), replyTo),
                        timeout,
                        system.scheduler())
                .thenApply(response -> response.exists);
    }
}
