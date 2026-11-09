package io.github.seonwkim.example.agent;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.actor.SupervisionStrategy;
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

/**
 * The agent loop for one agent id: user message, LLM call, tool calls, final answer.
 *
 * <p>Nothing here blocks the actor. The LLM call is piped back as a message, the rate limiter
 * answers with a message, and the human approval is an ask with a timeout. While a call is
 * pending the agent keeps answering {@link AgentActor.GetConversation} and queues new chats.
 */
public final class AgentBrain {

    // Replies the brain sends itself. They never leave the node, so they are never serialized.
    private record PermitGranted(RateLimiterActor.Permit permit) implements AgentActor.Command {}

    private record LlmResponded(LlmReply reply, Throwable error) implements AgentActor.Command {}

    private record ApprovalResolved(LlmReply call, boolean approved, String reason) implements AgentActor.Command {}

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
            SpringActorHandle<RateLimiterActor.Command> rateLimiter,
            SpringActorHandle<ApprovalActor.Command> approvals,
            Duration approvalTimeout,
            int maxToolSteps) {}

    // --8<-- [start:supervision]
    /**
     * Restart on failure, at most 10 times a minute. A restart creates the state again, so the
     * brain reloads its conversation from the last snapshot. After the limit the entity stops,
     * and sharding starts a fresh one on the next message.
     */
    public static SpringActorBehavior<AgentActor.Command> behavior(
            SpringActorBehavior.Builder<AgentActor.Command, SpringBehaviorContext<AgentActor.Command>> builder,
            String agentId,
            Dependencies dependencies) {
        return builder.withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
                .withState(ctx -> new AgentBrain(ctx, agentId, dependencies))
                .onMessage(AgentActor.Chat.class, AgentBrain::onChat)
                .onMessage(AgentActor.GetConversation.class, AgentBrain::onGetConversation)
                .onMessage(AgentActor.SimulateCrash.class, AgentBrain::onSimulateCrash)
                .onMessage(PermitGranted.class, AgentBrain::onPermit)
                .onMessage(LlmResponded.class, AgentBrain::onLlmResponded)
                .onMessage(ApprovalResolved.class, AgentBrain::onApprovalResolved)
                .build();
    }

    private AgentBrain(SpringBehaviorContext<AgentActor.Command> ctx, String agentId, Dependencies dependencies) {
        this.ctx = ctx;
        this.agentId = agentId;
        this.deps = dependencies;
        this.permits = ctx.messageAdapter(RateLimiterActor.Permit.class, PermitGranted::new);
        dependencies.snapshots().load(agentId).ifPresent(snapshot -> conversation.addAll(snapshot.conversation()));
    }
    // --8<-- [end:supervision]

    private final SpringBehaviorContext<AgentActor.Command> ctx;
    private final String agentId;
    private final Dependencies deps;
    private final SpringActorRef<RateLimiterActor.Permit> permits;
    private final String incarnation = UUID.randomUUID().toString();
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final Deque<AgentActor.Chat> queued = new ArrayDeque<>();
    private Status status = Status.IDLE;
    private AgentActor.Chat current;
    private int toolSteps;

    private SpringActorBehavior<AgentActor.Command> onChat(AgentActor.Chat chat) {
        if (status != Status.IDLE) {
            queued.addLast(chat);
            return SpringActorBehavior.same();
        }
        startTurn(chat);
        return SpringActorBehavior.same();
    }

    private SpringActorBehavior<AgentActor.Command> onGetConversation(AgentActor.GetConversation query) {
        query.reply(
                new ConversationView(agentId, incarnation, status.name(), queued.size(), List.copyOf(conversation)));
        return SpringActorBehavior.same();
    }

    private SpringActorBehavior<AgentActor.Command> onSimulateCrash(AgentActor.SimulateCrash crash) {
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

    private SpringActorBehavior<AgentActor.Command> onPermit(PermitGranted granted) {
        if (status != Status.PERMIT) {
            return SpringActorBehavior.same();
        }
        status = Status.LLM;
        ctx.getLog().debug("Agent {} calls the LLM", agentId);
        // The future completes on another thread; pipeToSelf turns it into a message, so
        // the actor never waits and never touches its state from that thread.
        ctx.pipeToSelf(deps.llm().complete(List.copyOf(conversation)), LlmResponded::new);
        return SpringActorBehavior.same();
    }

    private SpringActorBehavior<AgentActor.Command> onLlmResponded(LlmResponded responded) {
        if (status != Status.LLM) {
            return SpringActorBehavior.same(); // a reply for an earlier incarnation
        }
        if (responded.error() != null) {
            finishTurn("Sorry, the model call failed: " + responded.error().getMessage());
            return SpringActorBehavior.same();
        }
        LlmReply reply = responded.reply();
        if (!reply.isToolCall()) {
            finishTurn(reply.text());
            return SpringActorBehavior.same();
        }
        if (++toolSteps > deps.maxToolSteps()) {
            finishTurn("Stopped after " + deps.maxToolSteps() + " tool calls.");
            return SpringActorBehavior.same();
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
        return SpringActorBehavior.same();
    }
    // --8<-- [end:llm-call]

    // --8<-- [start:approval]
    private void askForApproval(LlmReply call) {
        status = Status.APPROVAL;
        PendingApproval request =
                new PendingApproval(agentId + "-" + UUID.randomUUID(), agentId, call.toolName(), call.toolArgument());
        Duration timeout = deps.approvalTimeout();
        ctx.pipeToSelf(
                deps.approvals()
                        .ask(new ApprovalActor.RequestApproval(request, timeout))
                        .withTimeout(timeout)
                        .execute(),
                (ApprovalDecision decision, Throwable error) -> {
                    if (error != null) {
                        return new ApprovalResolved(call, false, "no human answer within " + timeout);
                    }
                    return new ApprovalResolved(
                            call, decision.approved(), decision.approved() ? "approved" : "rejected by a human");
                });
    }

    private SpringActorBehavior<AgentActor.Command> onApprovalResolved(ApprovalResolved resolved) {
        if (status != Status.APPROVAL) {
            return SpringActorBehavior.same();
        }
        LlmReply call = resolved.call();
        Optional<Tool> tool = deps.tools().find(call.toolName());
        if (resolved.approved() && tool.isPresent()) {
            runTool(tool.get(), call);
        } else {
            toolResult(call, "not run: " + resolved.reason());
        }
        return SpringActorBehavior.same();
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
