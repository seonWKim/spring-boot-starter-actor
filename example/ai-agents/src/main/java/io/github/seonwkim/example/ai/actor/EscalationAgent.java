package io.github.seonwkim.example.ai.actor;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.example.ai.config.AIConfiguration;
import io.github.seonwkim.example.ai.model.Sentiment;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Agent responsible for escalating issues to human agents.
 * Creates tickets and provides handoff messaging.
 */
@Component
public class EscalationAgent implements SpringActor<EscalationAgent.Command> {

    private final AIConfiguration aiConfig;

    public EscalationAgent(AIConfiguration aiConfig) {
        this.aiConfig = aiConfig;
    }

    public interface Command {}

    /**
     * Escalate an issue to a human agent
     */
    public static class EscalateIssue extends AskCommand<String> implements Command {
        public final String userId;
        public final String message;
        public final Sentiment sentiment;

        public EscalateIssue(String userId, String message, Sentiment sentiment) {
            this.userId = userId;
            this.message = message;
            this.sentiment = sentiment;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new EscalationBehavior(ctx, aiConfig))
                .onMessage(EscalateIssue.class, EscalationBehavior::handleEscalate)
                .build();
    }

    private static class EscalationBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final AIConfiguration aiConfig;

        EscalationBehavior(SpringBehaviorContext<Command> ctx, AIConfiguration aiConfig) {
            this.ctx = ctx;
            this.aiConfig = aiConfig;
        }

        private Behavior<Command> handleEscalate(
                EscalateIssue msg) {

            ctx.getLog()
                    .warn(
                            "Escalating issue for user {} with sentiment: {}",
                            msg.userId,
                            msg.sentiment);

            // Generate ticket ID
            String ticketId = generateTicketId();

            // Determine priority based on sentiment
            String priority = (msg.sentiment == Sentiment.NEGATIVE) ? "HIGH" : "NORMAL";

            // In a real system, this would:
            // 1. Create ticket in support system (e.g., Zendesk, JIRA)
            // 2. Notify human agents (e.g., Slack, email)
            // 3. Store ticket details in database
            ctx.getLog()
                    .info(
                            "Created {} priority ticket: {} for user: {}",
                            priority,
                            ticketId,
                            msg.userId);

            // Build response message
            String response = buildEscalationResponse(ticketId, priority);

            msg.reply(response);
            return Behaviors.same();
        }

        private String generateTicketId() {
            // Simple ticket ID generation: TICK-XXXXX
            long timestamp = System.currentTimeMillis();
            int random = (int) (timestamp % 100000);
            return String.format("TICK-%05d", random);
        }

        private String buildEscalationResponse(String ticketId, String priority) {
            String baseMessage = aiConfig.getFallback().getEscalationMessage();

            return String.format(
                    "%s\n\n"
                            + "📋 Ticket ID: %s\n"
                            + "⏱️ Priority: %s\n"
                            + "⏳ Expected response: %s\n\n"
                            + "You'll receive an email confirmation shortly with ticket details.",
                    baseMessage,
                    ticketId,
                    priority,
                    priority.equals("HIGH") ? "within 15 minutes" : "within 2 hours");
        }
    }
}
