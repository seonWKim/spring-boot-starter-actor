package io.github.seonwkim.example.agent.approval;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringBehaviorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * One per node: holds tool calls that wait for a human. An agent asks with {@link RequestApproval}
 * and a timeout; a human lists {@link ListPending} and answers with {@link Decide}. A request
 * that outlives the agent's timeout is dropped by a timer, so a late answer is refused.
 */
@Component
public class ApprovalActor implements SpringActor<ApprovalActor.Command> {

    public interface Command {}

    /** Sent by an agent with ask; the reply is the human's decision. */
    public static final class RequestApproval extends AskCommand<ApprovalDecision> implements Command {
        private final PendingApproval request;
        private final Duration timeout;

        public RequestApproval(PendingApproval request, Duration timeout) {
            this.request = request;
            this.timeout = timeout;
        }

        public PendingApproval request() {
            return request;
        }

        public Duration timeout() {
            return timeout;
        }
    }

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
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(Gateway::new)
                .onMessage(RequestApproval.class, Gateway::onRequest)
                .onMessage(ListPending.class, Gateway::onList)
                .onMessage(Decide.class, Gateway::onDecide)
                .onMessage(Expire.class, Gateway::onExpire)
                .build();
    }

    private static final class Gateway {
        private final SpringBehaviorContext<Command> ctx;
        private final Map<String, RequestApproval> pending = new LinkedHashMap<>();

        Gateway(SpringBehaviorContext<Command> ctx) {
            this.ctx = ctx;
        }

        SpringActorBehavior<Command> onRequest(RequestApproval request) {
            String id = request.request().requestId();
            pending.put(id, request);
            ctx.startSingleTimer(id, new Expire(id), request.timeout());
            return SpringActorBehavior.same();
        }

        SpringActorBehavior<Command> onList(ListPending list) {
            List<PendingApproval> view = new ArrayList<>();
            pending.values().forEach(request -> view.add(request.request()));
            list.reply(List.copyOf(view));
            return SpringActorBehavior.same();
        }

        SpringActorBehavior<Command> onDecide(Decide decide) {
            RequestApproval request = pending.remove(decide.requestId);
            if (request == null) {
                decide.reply(false);
                return SpringActorBehavior.same();
            }
            ctx.cancelTimer(decide.requestId);
            request.reply(new ApprovalDecision(decide.approved));
            decide.reply(true);
            return SpringActorBehavior.same();
        }

        SpringActorBehavior<Command> onExpire(Expire expire) {
            pending.remove(expire.requestId());
            return SpringActorBehavior.same();
        }
    }
}
