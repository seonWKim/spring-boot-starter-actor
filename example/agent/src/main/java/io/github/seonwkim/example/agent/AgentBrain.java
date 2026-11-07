package io.github.seonwkim.example.agent;

import io.github.seonwkim.example.agent.approval.ApprovalActor;
import io.github.seonwkim.example.agent.approval.ApprovalDecision;
import io.github.seonwkim.example.agent.approval.PendingApproval;
import io.github.seonwkim.example.agent.llm.ChatMessage;
import io.github.seonwkim.example.agent.llm.LlmClient;
import io.github.seonwkim.example.agent.llm.LlmReply;
import io.github.seonwkim.example.agent.persistence.AgentSnapshot;
import io.github.seonwkim.example.agent.persistence.AgentSnapshotStore;
import io.github.seonwkim.example.agent.ratelimit.RateLimiterActor;
import io.github.seonwkim.example.agent.tool.Tool;
import io.github.seonwkim.example.agent.tool.ToolRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * The agent loop for one agent id: user message, LLM call, tool calls, final answer.
 *
 * <p>Nothing here blocks the actor. The LLM call is piped back as a message, the rate limiter
 * answers with a message, and the human approval is an ask with a timeout. While a call is
 * pending the agent keeps answering {@link AgentActor.GetConversation} and queues new chats.
 */
public final class AgentBrain {

    /** Everything the brain handles: the public {@link AgentActor.Command}s and internal replies. */
    public interface Message {}

    private record PermitGranted(RateLimiterActor.Permit permit) implements Message {}

    private record LlmResponded(LlmReply reply, Throwable error) implements Message {}

    private record ApprovalResolved(LlmReply call, boolean approved, String reason) implements Message {}

    private enum Status {
        IDLE,
        PERMIT,
        LLM,
        APPROVAL
    }

    /** Beans and node-local actors the brain uses. */
    public record Dependencies(
            LlmClient llm,
            ToolRegistry tools,
            AgentSnapshotStore snapshots,
            ActorRef<RateLimiterActor.Command> rateLimiter,
            ActorRef<ApprovalActor.Command> approvals,
            Duration approvalTimeout,
            int maxToolSteps) {}

    // --8<-- [start:supervision]
    /**
     * Restart on failure. {@code Behaviors.setup} sits inside the supervisor, so a restart runs
     * it again and the brain reloads its conversation from the last snapshot.
     */
    public static Behavior<Message> create(String agentId, Dependencies dependencies) {
        return Behaviors.supervise(
                        Behaviors.<Message>setup(ctx -> new AgentBrain(ctx, agentId, dependencies).behavior()))
                .onFailure(SupervisorStrategy.restart().withLimit(10, Duration.ofMinutes(1)));
    }

    private AgentBrain(ActorContext<Message> ctx, String agentId, Dependencies dependencies) {
        this.ctx = ctx;
        this.agentId = agentId;
        this.deps = dependencies;
        this.permits = ctx.messageAdapter(RateLimiterActor.Permit.class, PermitGranted::new);
        dependencies.snapshots().load(agentId).ifPresent(snapshot -> conversation.addAll(snapshot.conversation()));
    }
    // --8<-- [end:supervision]

    private final ActorContext<Message> ctx;
    private final String agentId;
    private final Dependencies deps;
    private final ActorRef<RateLimiterActor.Permit> permits;
    private final String incarnation = UUID.randomUUID().toString();
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final Deque<AgentActor.Chat> queued = new ArrayDeque<>();
    private Status status = Status.IDLE;
    private AgentActor.Chat current;
    private int toolSteps;

    private Behavior<Message> behavior() {
        return Behaviors.receive(Message.class)
                .onMessage(AgentActor.Chat.class, this::onChat)
                .onMessage(AgentActor.GetConversation.class, this::onGetConversation)
                .onMessage(AgentActor.SimulateCrash.class, this::onSimulateCrash)
                .onMessage(PermitGranted.class, this::onPermit)
                .onMessage(LlmResponded.class, this::onLlmResponded)
                .onMessage(ApprovalResolved.class, this::onApprovalResolved)
                .build();
    }

    private Behavior<Message> onChat(AgentActor.Chat chat) {
        if (status != Status.IDLE) {
            queued.addLast(chat);
            return Behaviors.same();
        }
        startTurn(chat);
        return Behaviors.same();
    }

    private Behavior<Message> onGetConversation(AgentActor.GetConversation query) {
        query.reply(
                new ConversationView(agentId, incarnation, status.name(), queued.size(), List.copyOf(conversation)));
        return Behaviors.same();
    }

    private Behavior<Message> onSimulateCrash(AgentActor.SimulateCrash crash) {
        throw new IllegalStateException("simulated crash of agent " + agentId);
    }

    private void startTurn(AgentActor.Chat chat) {
        ctx.getLog().debug("Agent {} starts a turn", agentId);
        current = chat;
        toolSteps = 0;
        append(ChatMessage.user(chat.text));
        requestPermit();
    }

    // --8<-- [start:llm-call]
    private void requestPermit() {
        status = Status.PERMIT;
        deps.rateLimiter().tell(RateLimiterActor.Acquire.replyingTo(permits));
    }

    private Behavior<Message> onPermit(PermitGranted granted) {
        if (status != Status.PERMIT) {
            return Behaviors.same();
        }
        status = Status.LLM;
        ctx.getLog().debug("Agent {} calls the LLM", agentId);
        // The future completes on another thread; pipeToSelf turns it into a message, so
        // the actor never waits and never touches its state from that thread.
        ctx.pipeToSelf(deps.llm().complete(List.copyOf(conversation)), LlmResponded::new);
        return Behaviors.same();
    }

    private Behavior<Message> onLlmResponded(LlmResponded responded) {
        if (status != Status.LLM) {
            return Behaviors.same(); // a reply for an earlier incarnation
        }
        if (responded.error() != null) {
            finishTurn("Sorry, the model call failed: " + responded.error().getMessage());
            return Behaviors.same();
        }
        LlmReply reply = responded.reply();
        if (!reply.isToolCall()) {
            finishTurn(reply.text());
            return Behaviors.same();
        }
        if (++toolSteps > deps.maxToolSteps()) {
            finishTurn("Stopped after " + deps.maxToolSteps() + " tool calls.");
            return Behaviors.same();
        }
        append(ChatMessage.toolCall(reply.toolName(), reply.toolArgument()));
        Optional<Tool> tool = deps.tools().find(reply.toolName());
        if (tool.isEmpty()) {
            toolResult(reply, "unknown tool");
        } else if (tool.get().requiresApproval()) {
            askForApproval(reply);
        } else {
            runTool(tool.get(), reply);
        }
        return Behaviors.same();
    }
    // --8<-- [end:llm-call]

    // --8<-- [start:approval]
    private void askForApproval(LlmReply call) {
        status = Status.APPROVAL;
        PendingApproval request = new PendingApproval(
                agentId + "-" + UUID.randomUUID(), agentId, call.toolName(), call.toolArgument());
        Duration timeout = deps.approvalTimeout();
        ctx.ask(
                ApprovalDecision.class,
                deps.approvals(),
                timeout,
                replyTo -> new ApprovalActor.RequestApproval(request, timeout, replyTo),
                (decision, error) -> {
                    if (error != null) {
                        return new ApprovalResolved(call, false, "no human answer within " + timeout);
                    }
                    return new ApprovalResolved(
                            call, decision.approved(), decision.approved() ? "approved" : "rejected by a human");
                });
    }

    private Behavior<Message> onApprovalResolved(ApprovalResolved resolved) {
        if (status != Status.APPROVAL) {
            return Behaviors.same();
        }
        LlmReply call = resolved.call();
        Optional<Tool> tool = deps.tools().find(call.toolName());
        if (resolved.approved() && tool.isPresent()) {
            runTool(tool.get(), call);
        } else {
            toolResult(call, "not run: " + resolved.reason());
        }
        return Behaviors.same();
    }
    // --8<-- [end:approval]

    private void runTool(Tool tool, LlmReply call) {
        String output;
        try {
            output = tool.invoke(call.toolArgument());
        } catch (RuntimeException e) {
            output = "tool failed: " + e.getMessage();
        }
        toolResult(call, output);
    }

    private void toolResult(LlmReply call, String output) {
        append(ChatMessage.toolResult(call.toolName(), output));
        requestPermit();
    }

    private void finishTurn(String answer) {
        ctx.getLog().debug("Agent {} answers: {}", agentId, answer);
        append(ChatMessage.assistant(answer));
        if (current != null && current.hasReplyTo()) {
            current.reply(new AgentAnswer(agentId, answer));
        }
        current = null;
        status = Status.IDLE;
        AgentActor.Chat next = queued.pollFirst();
        if (next != null) {
            startTurn(next);
        }
    }

    // --8<-- [start:snapshot]
    /** Every change to the conversation is written before the agent moves on. */
    private void append(ChatMessage message) {
        conversation.add(message);
        deps.snapshots().save(new AgentSnapshot(agentId, List.copyOf(conversation)));
    }
    // --8<-- [end:snapshot]
}
