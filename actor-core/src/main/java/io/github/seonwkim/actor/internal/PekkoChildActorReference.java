package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SpringChildActorReference;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/** Pekko-backed {@link SpringChildActorReference}. */
public final class PekkoChildActorReference<P, C> implements SpringChildActorReference<P, C> {
    private final ActorRef<P> parentRef;
    private final ActorSystem<?> system;
    private final Class<? extends SpringActorWithContext<C, ?>> childActorClass;
    private final String childId;
    private final Duration defaultTimeout;
    private Duration timeout;

    /**
     * Creates a new SpringChildActorReference.
     *
     * @param parentRef The parent actor reference
     * @param system The actor system, for asks and tracing
     * @param childActorClass The child actor class
     * @param childId The unique ID of the child actor
     * @param defaultTimeout The default timeout for operations
     */
    public PekkoChildActorReference(
            ActorRef<P> parentRef,
            ActorSystem<?> system,
            Class<? extends SpringActorWithContext<C, ?>> childActorClass,
            String childId,
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
        if (childId == null || childId.trim().isEmpty()) {
            throw new IllegalArgumentException("childId must not be null or empty");
        }
        this.parentRef = parentRef;
        this.system = system;
        this.childActorClass = childActorClass;
        this.childId = childId;
        this.defaultTimeout = defaultTimeout;
        this.timeout = defaultTimeout;
    }

    @Override
    public SpringChildActorReference<P, C> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Optional<SpringActorHandle<C>>> get() {
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.GetChildResponse<C>> replyTo) ->
                                new FrameworkCommands.GetChild<>(childActorClass, childId, replyTo),
                        timeout,
                        system.scheduler())
                .thenApply(response -> {
                    if (response.found && response.childRef != null) {
                        return Optional.of(new PekkoActorHandle<>(system, response.childRef, defaultTimeout));
                    } else {
                        return Optional.empty();
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletionStage<Boolean> exists() {
        ActorRef<Object> parentAsObject = (ActorRef<Object>) parentRef;

        return AskPattern.ask(
                        parentAsObject,
                        (ActorRef<FrameworkCommands.ExistsChildResponse> replyTo) ->
                                new FrameworkCommands.ExistsChild<>(childActorClass, childId, replyTo),
                        timeout,
                        system.scheduler())
                .thenApply(response -> response.exists);
    }
}
