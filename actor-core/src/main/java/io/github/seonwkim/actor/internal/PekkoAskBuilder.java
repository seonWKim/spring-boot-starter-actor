package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorHandle;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/** An ask through Pekko's {@link AskPattern}, to an actor or a sharded entity. */
public final class PekkoAskBuilder<REQ, RES> implements SpringActorHandle.AskBuilder<RES> {

    private final RecipientRef<REQ> target;
    private final Scheduler scheduler;
    private final Function<ActorRef<RES>, REQ> messageFactory;
    private Duration timeout;

    @Nullable private Supplier<RES> timeoutHandler;

    public PekkoAskBuilder(
            RecipientRef<REQ> target,
            Scheduler scheduler,
            Duration defaultTimeout,
            Function<ActorRef<RES>, REQ> messageFactory) {
        this.target = target;
        this.scheduler = scheduler;
        this.timeout = defaultTimeout;
        this.messageFactory = messageFactory;
    }

    @Override
    public PekkoAskBuilder<REQ, RES> withTimeout(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException("timeout must not be null");
        }
        this.timeout = timeout;
        return this;
    }

    @Override
    public PekkoAskBuilder<REQ, RES> onTimeout(Supplier<RES> defaultValueSupplier) {
        this.timeoutHandler = defaultValueSupplier;
        return this;
    }

    @Override
    public CompletionStage<RES> execute() {
        CompletionStage<RES> result = AskPattern.ask(target, messageFactory::apply, timeout, scheduler);
        if (timeoutHandler == null) {
            return result;
        }
        Supplier<RES> handler = timeoutHandler;
        return result.exceptionally(throwable -> {
            if (throwable instanceof TimeoutException || throwable.getCause() instanceof TimeoutException) {
                return handler.get();
            }
            if (throwable instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(throwable);
        });
    }
}
