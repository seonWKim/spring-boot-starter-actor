package io.github.seonwkim.example.agent.approval;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.springframework.stereotype.Component;

/**
 * One per node: holds tool calls that wait for a human. An agent asks with {@link RequestApproval}
 * and a timeout; a human lists {@link ListPending} and answers with {@link Decide}. A request
 * that outlives the agent's timeout is dropped by a timer, so a late answer is refused.
 */
@Component
public class ApprovalActor implements SpringActor<ApprovalActor.Command> {

    public interface Command {}

    /** Sent by an agent with Pekko's ask; {@code replyTo} is the ask's temporary actor. */
    public record RequestApproval(PendingApproval request, Duration timeout, ActorRef<ApprovalDecision> replyTo)
            implements Command {}

    public static final class ListPending extends AskCommand<List<PendingApproval>> implements Command {}

    /** A human's answer; replies true if the request was still waiting. */
    public static final class Decide extends AskCommand<Boolean> implements Command {
        private final String requestId;
        private final boolean approved;

        public Decide(String requestId, boolean approved) {
            this.requestId = requestId;
            this.approved = approved;
        }
    }

    private record Expire(String requestId) implements Command {}

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.wrap(Behaviors.withTimers(timers -> new Gateway(timers).behavior()));
    }

    private static final class Gateway {
        private final TimerScheduler<Command> timers;
        private final Map<String, RequestApproval> pending = new LinkedHashMap<>();

        Gateway(TimerScheduler<Command> timers) {
            this.timers = timers;
        }

        Behavior<Command> behavior() {
            return Behaviors.receive(Command.class)
                    .onMessage(RequestApproval.class, this::onRequest)
                    .onMessage(ListPending.class, this::onList)
                    .onMessage(Decide.class, this::onDecide)
                    .onMessage(Expire.class, this::onExpire)
                    .build();
        }

        private Behavior<Command> onRequest(RequestApproval request) {
            String id = request.request().requestId();
            pending.put(id, request);
            timers.startSingleTimer(id, new Expire(id), request.timeout());
            return Behaviors.same();
        }

        private Behavior<Command> onList(ListPending list) {
            List<PendingApproval> view = new ArrayList<>();
            pending.values().forEach(request -> view.add(request.request()));
            list.reply(List.copyOf(view));
            return Behaviors.same();
        }

        private Behavior<Command> onDecide(Decide decide) {
            RequestApproval request = pending.remove(decide.requestId);
            if (request == null) {
                decide.reply(false);
                return Behaviors.same();
            }
            timers.cancel(decide.requestId);
            request.replyTo().tell(new ApprovalDecision(decide.approved));
            decide.reply(true);
            return Behaviors.same();
        }

        private Behavior<Command> onExpire(Expire expire) {
            pending.remove(expire.requestId());
            return Behaviors.same();
        }
    }
}
