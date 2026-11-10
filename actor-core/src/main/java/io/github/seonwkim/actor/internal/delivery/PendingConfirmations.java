package io.github.seonwkim.actor.internal.delivery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A sender actor's messages waiting for demand, and the stages of those sent but not yet confirmed.
 * Only the sender actor touches it.
 */
public final class PendingConfirmations<M> {

    private final Deque<Waiting<M>> waiting = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> unconfirmed = new ArrayList<>();

    /** A message waiting for demand from the producer controller. */
    public record Waiting<M>(M message, CompletableFuture<Void> confirmed) {}

    public void add(M message, CompletableFuture<Void> confirmed) {
        waiting.add(new Waiting<>(message, confirmed));
    }

    /** The next waiting message, now counted as sent, or null when none waits. */
    public @Nullable Waiting<M> next() {
        Waiting<M> next = waiting.poll();
        if (next != null) {
            unconfirmed.removeIf(CompletableFuture::isDone);
            unconfirmed.add(next.confirmed());
        }
        return next;
    }

    /** Fails every stage not yet confirmed. */
    public void failAll(String reason) {
        IllegalStateException failure = new IllegalStateException(reason);
        Consumer<CompletableFuture<Void>> fail = stage -> stage.completeExceptionally(failure);
        waiting.forEach(w -> fail.accept(w.confirmed()));
        unconfirmed.forEach(fail);
        waiting.clear();
        unconfirmed.clear();
    }
}
