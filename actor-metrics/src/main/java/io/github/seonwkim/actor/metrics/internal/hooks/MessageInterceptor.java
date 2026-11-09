package io.github.seonwkim.actor.metrics.internal.hooks;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.PreRestart;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Counts and times every message an actor processes, and counts the ones that throw. It runs inside
 * the actor's supervision, so a failure passes through it before the supervisor sees it, and the
 * supervisor's {@link PreRestart} reaches it.
 */
final class MessageInterceptor extends BehaviorInterceptor<Object, Object> {

    private final MessageMeters meters;

    private MessageInterceptor(MessageMeters meters) {
        super(Object.class);
        this.meters = meters;
    }

    @SuppressWarnings("unchecked")
    static <T> Behavior<T> intercept(MessageMeters meters, Behavior<T> behavior) {
        return (Behavior<T>) Behaviors.intercept(() -> new MessageInterceptor(meters), (Behavior<Object>) behavior);
    }

    @Override
    public Behavior<Object> aroundReceive(TypedActorContext<Object> ctx, Object msg, ReceiveTarget<Object> target) {
        long start = System.nanoTime();
        try {
            return target.apply(ctx, msg);
        } catch (Throwable failure) {
            meters.failures().increment();
            throw failure;
        } finally {
            Timer processingTime = meters.processingTime();
            if (processingTime != null) {
                processingTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            }
        }
    }

    @Override
    public Behavior<Object> aroundSignal(TypedActorContext<Object> ctx, Signal signal, SignalTarget<Object> target) {
        Counter restarts = meters.restarts();
        if (signal instanceof PreRestart && restarts != null) {
            restarts.increment();
        }
        return target.apply(ctx, signal);
    }
}
