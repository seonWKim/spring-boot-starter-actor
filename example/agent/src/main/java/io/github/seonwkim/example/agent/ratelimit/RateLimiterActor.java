package io.github.seonwkim.example.agent.ratelimit;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.example.agent.AgentProperties;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.springframework.stereotype.Component;

// --8<-- [start:rate-limiter]
/**
 * One per node: hands out permits for LLM calls, at most {@code agent.calls-per-second} in any
 * one-second window. Requests over the limit wait in a queue; a timer wakes the actor when the
 * oldest permit leaves the window.
 *
 * <p>The starter has no rate limiter and no timer wrapper, so this actor uses Pekko's
 * {@code Behaviors.withTimers} and is wrapped with {@link SpringActorBehavior#wrap}.
 */
@Component
public class RateLimiterActor implements SpringActor<RateLimiterActor.Command> {

    public interface Command {}

    /** Ask for one permit. The reply comes when the call may start. */
    public static final class Acquire extends AskCommand<Permit> implements Command {

        /** For actors that want the permit as a message instead of a future. */
        public static Acquire replyingTo(ActorRef<Permit> replyTo) {
            Acquire acquire = new Acquire();
            acquire.withReplyTo(replyTo);
            return acquire;
        }
    }

    /** @param grantedAtNanos {@link System#nanoTime()} when the permit was granted */
    public record Permit(long grantedAtNanos) {}

    private enum WakeUp implements Command {
        INSTANCE
    }

    private static final Duration WINDOW = Duration.ofSeconds(1);

    private final int callsPerSecond;

    public RateLimiterActor(AgentProperties properties) {
        this.callsPerSecond = properties.getCallsPerSecond();
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.wrap(Behaviors.<Command>withTimers(
                timers -> new SlidingWindow(timers, callsPerSecond, WINDOW.toNanos()).behavior()));
    }

    private static final class SlidingWindow {
        private final TimerScheduler<Command> timers;
        private final int limit;
        private final long windowNanos;
        private final Deque<Long> granted = new ArrayDeque<>();
        private final Deque<Acquire> waiting = new ArrayDeque<>();

        SlidingWindow(TimerScheduler<Command> timers, int limit, long windowNanos) {
            this.timers = timers;
            this.limit = limit;
            this.windowNanos = windowNanos;
        }

        Behavior<Command> behavior() {
            return Behaviors.receive(Command.class)
                    .onMessage(Acquire.class, acquire -> {
                        waiting.addLast(acquire);
                        return drain();
                    })
                    .onMessage(WakeUp.class, wakeUp -> drain())
                    .build();
        }

        private Behavior<Command> drain() {
            long now = System.nanoTime();
            while (!granted.isEmpty() && now - granted.peekFirst() >= windowNanos) {
                granted.removeFirst();
            }
            while (!waiting.isEmpty() && granted.size() < limit) {
                granted.addLast(now);
                waiting.removeFirst().reply(new Permit(now));
            }
            if (!waiting.isEmpty()) {
                long untilFree = windowNanos - (now - granted.peekFirst());
                timers.startSingleTimer(WakeUp.INSTANCE, WakeUp.INSTANCE, Duration.ofNanos(untilFree));
            }
            return Behaviors.same();
        }
    }
}
// --8<-- [end:rate-limiter]
