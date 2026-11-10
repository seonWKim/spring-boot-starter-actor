package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.DispatcherConfig;
import io.github.seonwkim.actor.MailboxConfig;
import io.github.seonwkim.actor.MdcConfig;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSpawnBuilder;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import io.github.seonwkim.actor.TagsConfig;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.springframework.util.ObjectUtils;

/** Pekko-backed {@link SpringActorSpawnBuilder}. */
public final class PekkoActorSpawnBuilder<A extends SpringActorWithContext<C, ?>, C>
        implements SpringActorSpawnBuilder<A, C> {
    private final PekkoActorSystem actorSystem;
    private final Class<A> actorClass;

    @Nullable private String actorId;

    @Nullable private SpringActorContext actorContext;

    private Duration timeout = ActorConstants.DEFAULT_TIMEOUT;
    private MailboxConfig mailboxConfig = MailboxConfig.defaultMailbox();
    private DispatcherConfig dispatcherConfig = DispatcherConfig.defaultDispatcher();
    private TagsConfig tagsConfig = TagsConfig.empty();
    private MdcConfig mdcConfig = MdcConfig.empty();
    private boolean isClusterSingleton = false;

    @Nullable private SupervisionStrategy supervisorStrategy = null;

    /**
     * Creates a new SpringActorSpawnBuilder.
     *
     * @param actorSystem The actor system to spawn the actor in
     * @param actorClass The class of the actor to spawn
     */
    public PekkoActorSpawnBuilder(PekkoActorSystem actorSystem, Class<A> actorClass) {
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must not be null");
        }
        if (actorClass == null) {
            throw new IllegalArgumentException("actorClass must not be null");
        }
        this.actorSystem = actorSystem;
        this.actorClass = actorClass;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withId(String actorId) {
        if (actorId == null || ObjectUtils.isEmpty(actorId)) {
            throw new IllegalArgumentException("actorId must not be null or empty");
        }
        this.actorId = actorId;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withContext(SpringActorContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.actorContext = context;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withMailbox(MailboxConfig mailboxConfig) {
        if (mailboxConfig == null) {
            throw new IllegalArgumentException("mailboxConfig must not be null");
        }
        this.mailboxConfig = mailboxConfig;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withDispatcherFromConfig(String dispatcherPath) {
        this.dispatcherConfig = DispatcherConfig.fromConfig(dispatcherPath);
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withBlockingDispatcher() {
        this.dispatcherConfig = DispatcherConfig.blocking();
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withDefaultDispatcher() {
        this.dispatcherConfig = DispatcherConfig.defaultDispatcher();
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withDispatcherSameAsParent() {
        this.dispatcherConfig = DispatcherConfig.sameAsParent();
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withTags(TagsConfig tagsConfig) {
        if (tagsConfig == null) {
            throw new IllegalArgumentException("tagsConfig must not be null");
        }
        this.tagsConfig = tagsConfig;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withMdc(MdcConfig mdcConfig) {
        if (mdcConfig == null) {
            throw new IllegalArgumentException("mdcConfig must not be null");
        }
        this.mdcConfig = mdcConfig;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> asClusterSingleton(boolean isClusterSingleton) {
        this.isClusterSingleton = isClusterSingleton;
        return this;
    }

    @Override
    public SpringActorSpawnBuilder<A, C> asClusterSingleton() {
        return asClusterSingleton(true);
    }

    @Override
    public SpringActorSpawnBuilder<A, C> withSupervisionStrategy(SupervisionStrategy supervisorStrategy) {
        this.supervisorStrategy = supervisorStrategy;
        return this;
    }

    @Override
    public CompletionStage<SpringActorHandle<C>> spawn() {
        return spawn(false);
    }

    @Override
    public CompletionStage<SpringActorHandle<C>> getOrSpawn() {
        // The root guardian checks for a running actor and spawns in one step, so concurrent
        // callers asking for the same new id all get the same actor.
        return spawn(true);
    }

    private CompletionStage<SpringActorHandle<C>> spawn(boolean returnExisting) {
        if (actorContext == null) {
            String id = requireId();
            IllegalArgumentException mismatch = ActorSpawner.defaultContextMismatch(actorClass);
            if (mismatch != null) {
                return CompletableFuture.failedFuture(mismatch);
            }
            actorContext = new DefaultSpringActorContext(id);
        }

        // Apply MDC configuration to the context
        actorContext.setMdcConfig(mdcConfig);

        return actorSystem.spawn(
                actorClass,
                actorContext,
                mailboxConfig,
                dispatcherConfig,
                tagsConfig,
                isClusterSingleton,
                supervisorStrategy,
                returnExisting,
                timeout);
    }

    private String requireId() {
        if (actorId == null) {
            throw new IllegalStateException("Either actorId or actorContext must be set before spawning. "
                    + "Call withId(\"your-id\") or withContext(context) on the builder. "
                    + "Actor class: "
                    + actorClass.getName());
        }
        return actorId;
    }

    @Override
    public SpringActorHandle<C> spawnAndWait() {
        return spawn().toCompletableFuture().join();
    }
}
