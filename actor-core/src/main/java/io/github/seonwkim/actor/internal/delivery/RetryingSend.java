package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.delivery.ReliableDeliveryException;
import io.github.seonwkim.actor.delivery.ReliableDeliveryException.Reason;
import io.github.seonwkim.actor.delivery.RetryPolicy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/**
 * One reliable send: asks {@code target} with a {@link ReliableEnvelope} until it acks, with the
 * attempts and deadline of a {@link RetryPolicy} and exponential backoff with full jitter. Every
 * attempt carries the same key and message. A payload conflict ends the send at once.
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
        send.attempt(1, policy.initialBackoff(), Reason.TIMEOUT);
        return send.result;
    }

    private void attempt(int attempt, Duration backoff, Reason lastReason) {
        Duration remaining = Duration.ofNanos(deadlineNanos - System.nanoTime());
        if (remaining.isNegative() || remaining.isZero()) {
            giveUp(attempt - 1, lastReason, null);
            return;
        }
        Duration timeout = remaining.compareTo(policy.attemptTimeout()) < 0 ? remaining : policy.attemptTimeout();
        long startNanos = System.nanoTime();
        AskPattern.<ReliableEnvelope, ReliableAck>ask(
                        target, replyTo -> new ReliableEnvelope(key, message, replyTo), timeout, scheduler)
                .whenComplete((reply, failure) -> {
                    if (reply == ReliableAck.ACK) {
                        result.complete(null);
                        return;
                    }
                    Reason reason = reply == ReliableAck.PAYLOAD_CONFLICT
                            ? Reason.PAYLOAD_CONFLICT
                            : reply == ReliableAck.STALE_GENERATION
                                    ? Reason.STALE_GENERATION
                                    : reasonFor(failure, Duration.ofNanos(System.nanoTime() - startNanos), timeout);
                    Duration pause = jittered(backoff);
                    if (!reason.isRetryable()
                            || attempt >= policy.maxAttempts()
                            || Duration.ofNanos(deadlineNanos - System.nanoTime())
                                            .compareTo(pause)
                                    <= 0) {
                        giveUp(attempt, reason, failure);
                    } else {
                        Duration next = nextBackoff(backoff, policy.maxBackoff());
                        CompletableFuture.delayedExecutor(pause.toNanos(), TimeUnit.NANOSECONDS)
                                .execute(() -> attempt(attempt + 1, next, reason));
                    }
                });
    }

    /**
     * A timeout that came before the attempt's timeout did not wait for the receiver: the ask failed
     * at once, as it does for a stopped actor. So did any other failure.
     */
    private static Reason reasonFor(@Nullable Throwable failure, Duration waited, Duration attemptTimeout) {
        return failure instanceof TimeoutException && waited.compareTo(attemptTimeout.dividedBy(2)) >= 0
                ? Reason.TIMEOUT
                : Reason.UNAVAILABLE;
    }

    /** Twice {@code backoff}, at most {@code maxBackoff}. */
    static Duration nextBackoff(Duration backoff, Duration maxBackoff) {
        Duration next = backoff.multipliedBy(2);
        return next.compareTo(maxBackoff) > 0 ? maxBackoff : next;
    }

    /** Full jitter: a random pause from zero to {@code backoff}. */
    private static Duration jittered(Duration backoff) {
        return Duration.ofNanos(ThreadLocalRandom.current().nextLong(backoff.toNanos() + 1));
    }

    private void giveUp(int attempts, Reason reason, @Nullable Throwable lastFailure) {
        String what = reason == Reason.PAYLOAD_CONFLICT
                ? "The receiver handled idempotency key " + key + " with a different payload"
                : "Not acknowledged after " + attempts + " attempt(s) within " + policy.timeout() + " (idempotency key "
                        + key + ", max attempts " + policy.maxAttempts() + ")";
        result.completeExceptionally(
                new ReliableDeliveryException(what + ": " + reason, key, attempts, reason, lastFailure));
    }
}
