package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.delivery.ReliableDeliveryException;
import io.github.seonwkim.actor.delivery.RetryPolicy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/**
 * One reliable send: asks {@code target} with a {@link ReliableEnvelope} until it acks, with the
 * backoff, attempts and deadline of a {@link RetryPolicy}. Every attempt carries the same key.
 */
final class RetryingSend {

    private final RecipientRef<ReliableEnvelope> target;
    private final Object message;
    private final String key;
    private final RetryPolicy policy;
    private final Scheduler scheduler;
    private final long deadlineNanos;
    private final CompletableFuture<Void> result = new CompletableFuture<>();

    private RetryingSend(
            RecipientRef<ReliableEnvelope> target,
            Object message,
            String key,
            RetryPolicy policy,
            Scheduler scheduler) {
        this.target = target;
        this.message = message;
        this.key = key;
        this.policy = policy;
        this.scheduler = scheduler;
        this.deadlineNanos = System.nanoTime() + policy.timeout().toNanos();
    }

    @SuppressWarnings("unchecked")
    static CompletionStage<Void> start(
            RecipientRef<?> target, Object message, String key, RetryPolicy policy, Scheduler scheduler) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        RetryingSend send = new RetryingSend((RecipientRef<ReliableEnvelope>) target, message, key, policy, scheduler);
        send.attempt(1, policy.initialBackoff());
        return send.result;
    }

    private void attempt(int attempt, Duration backoff) {
        Duration remaining = Duration.ofNanos(deadlineNanos - System.nanoTime());
        if (remaining.isNegative() || remaining.isZero()) {
            giveUp(attempt - 1, null);
            return;
        }
        Duration timeout = remaining.compareTo(policy.attemptTimeout()) < 0 ? remaining : policy.attemptTimeout();
        AskPattern.<ReliableEnvelope, ReliableAck>ask(
                        target, replyTo -> new ReliableEnvelope(key, message, replyTo), timeout, scheduler)
                .whenComplete((ack, failure) -> {
                    if (failure == null) {
                        result.complete(null);
                    } else if (attempt >= policy.maxAttempts()) {
                        giveUp(attempt, failure);
                    } else if (Duration.ofNanos(deadlineNanos - System.nanoTime())
                                    .compareTo(backoff)
                            <= 0) {
                        giveUp(attempt, failure);
                    } else {
                        Duration next = backoff.multipliedBy(2);
                        Duration capped = next.compareTo(policy.maxBackoff()) > 0 ? policy.maxBackoff() : next;
                        CompletableFuture.delayedExecutor(backoff.toNanos(), TimeUnit.NANOSECONDS)
                                .execute(() -> attempt(attempt + 1, capped));
                    }
                });
    }

    private void giveUp(int attempts, @Nullable Throwable lastFailure) {
        result.completeExceptionally(new ReliableDeliveryException(
                "Not acknowledged after " + attempts + " attempt(s) within " + policy.timeout() + " (idempotency key "
                        + key + ", max attempts " + policy.maxAttempts() + ")",
                key,
                attempts,
                lastFailure));
    }
}
