package io.github.seonwkim.example.agent.ratelimit;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.example.agent.AgentProperties;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

// --8<-- [start:rate-limiter]
/**
 * One per node: hands out permits for LLM calls, at most {@code agent.calls-per-second} in any
 * one-second window. Requests over the limit wait in a queue; a timer wakes the actor when the
 * oldest permit leaves the window.
 *
 * <p>The starter has no rate limiter, so this is a plain actor with a timer from its behavior context.
 */
@Component
public class RateLimiterActor implements SpringActor<RateLimiterActor.Command> {

    public interface Command {}

    /** Ask for one permit. The reply comes when the call may start. */
    public static final class Acquire extends AskCommand<Permit> implements Command {

        @Nullable private final SpringActorRef<Permit> adapter;

        public Acquire() {
            this(null);
        }

        private Acquire(@Nullable SpringActorRef<Permit> adapter) {
            this.adapter = adapter;
        }

        /** For actors that want the permit as a message instead of a future. */
        public static Acquire replyingTo(SpringActorRef<Permit> replyTo) {
            return new Acquire(replyTo);
        }

        void grant(Permit permit) {
            if (adapter != null) {
                adapter.tell(permit);
            } else {
                reply(permit);
            }
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
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new SlidingWindow(ctx, callsPerSecond, WINDOW.toNanos()))
                .onMessage(Acquire.class, SlidingWindow::onAcquire)
                .onMessage(WakeUp.class, (window, wakeUp) -> window.drain())
                .build();
    }

    private static final class SlidingWindow {
        private final SpringBehaviorContext<Command> ctx;
        private final int limit;
        private final long windowNanos;
        private final Deque<Long> granted = new ArrayDeque<>();
        private final Deque<Acquire> waiting = new ArrayDeque<>();

        SlidingWindow(SpringBehaviorContext<Command> ctx, int limit, long windowNanos) {
            this.ctx = ctx;
            this.limit = limit;
            this.windowNanos = windowNanos;
        }

        SpringActorBehavior<Command> onAcquire(Acquire acquire) {
            waiting.addLast(acquire);
            return drain();
        }

        SpringActorBehavior<Command> drain() {
            long now = System.nanoTime();
            while (!granted.isEmpty() && now - granted.peekFirst() >= windowNanos) {
                granted.removeFirst();
            }
            while (!waiting.isEmpty() && granted.size() < limit) {
                granted.addLast(now);
                waiting.removeFirst().grant(new Permit(now));
            }
            if (!waiting.isEmpty()) {
                long untilFree = windowNanos - (now - granted.peekFirst());
                ctx.startSingleTimer(WakeUp.INSTANCE, WakeUp.INSTANCE, Duration.ofNanos(untilFree));
            }
            return SpringActorBehavior.same();
        }
    }
}
// --8<-- [end:rate-limiter]
