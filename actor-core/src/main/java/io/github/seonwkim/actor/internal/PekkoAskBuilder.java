package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/**
 * An ask through Pekko's {@link AskPattern}, to an actor or a sharded entity. It sends a copy of the
 * command that carries the reply address, so the caller's command is never changed.
 */
public final class PekkoAskBuilder<REQ, RES> implements SpringActorHandle.AskBuilder<RES> {

    private final RecipientRef<REQ> target;
    private final Scheduler scheduler;

    @Nullable private final MessageTracing tracing;

    private final AskCommand<RES> command;
    private Duration timeout;
    private boolean failFast;

    @Nullable private Supplier<RES> timeoutHandler;

    public PekkoAskBuilder(
            RecipientRef<REQ> target,
            Scheduler scheduler,
            @Nullable MessageTracing tracing,
            Duration defaultTimeout,
            AskCommand<RES> command) {
        this.target = target;
        this.scheduler = scheduler;
        this.tracing = tracing;
        this.timeout = defaultTimeout;
        this.command = command;
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
    public PekkoAskBuilder<REQ, RES> failFastOnError() {
        this.failFast = true;
        return this;
    }

    @Override
    public CompletionStage<RES> execute() {
        CompletionStage<RES> result =
                tracing == null ? AskPattern.ask(target, this::message, timeout, scheduler) : tracedAsk(tracing);
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

    /** The ask with the current trace; its send ends when the reply or the failure arrives. */
    @SuppressWarnings("unchecked")
    private CompletionStage<RES> tracedAsk(MessageTracing tracing) {
        MessageTracing.Sent[] sent = new MessageTracing.Sent[1];
        CompletionStage<RES> result = AskPattern.ask(
                target,
                replyTo -> {
                    REQ message = message(replyTo);
                    sent[0] = tracing.send(message, "ask");
                    return sent[0] == null ? message : (REQ) sent[0].message();
                },
                timeout,
                scheduler);
        if (sent[0] != null) {
            MessageTracing.Sent send = sent[0];
            result.whenComplete((reply, error) -> send.stop(error));
        }
        return result;
    }

    /** The command to send: a copy of the caller's, with this ask's reply address. */
    @SuppressWarnings("unchecked")
    private REQ message(ActorRef<RES> replyTo) {
        return (REQ) command.copyForAsk(new PekkoActorRef<>(replyTo), failFast);
    }
}
