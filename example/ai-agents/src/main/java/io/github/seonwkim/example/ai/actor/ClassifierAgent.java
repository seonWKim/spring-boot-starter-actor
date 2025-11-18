package io.github.seonwkim.example.ai.actor;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.example.ai.client.LLMClient;
import io.github.seonwkim.example.ai.model.Classification;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Agent responsible for classifying user messages into categories.
 * Uses LLM to determine intent: FAQ, TECHNICAL, BILLING, ESCALATION, etc.
 */
@Component
public class ClassifierAgent implements SpringActor<ClassifierAgent.Command> {

    private final LLMClient llmClient;

    public ClassifierAgent(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public interface Command {}

    /**
     * Classify a message into a category
     */
    public static class ClassifyMessage extends AskCommand<Classification> implements Command {
        public final String message;

        public ClassifyMessage(String message) {
            this.message = message;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onMessage(ClassifyMessage.class, this::handleClassify)
                .build();
    }

    private org.apache.pekko.actor.typed.Behavior<Command> handleClassify(
            SpringActorContext context, ClassifyMessage msg) {

        context.getLog().debug("Classifying message: {}", msg.message);

        String systemPrompt =
                """
                You are a customer support message classifier. Classify the user's message into ONE of these categories:
                - FAQ: General questions, how-to queries, basic information
                - TECHNICAL: Technical issues, bugs, errors, system problems
                - BILLING: Payment issues, refunds, charges, pricing questions
                - ESCALATION: Urgent issues, angry customers, requests for human agent
                - GENERAL: Greetings, general conversation, unclear intent

                Respond with ONLY the category name, nothing else.
                """;

        llmClient
                .chat(systemPrompt, msg.message)
                .thenAccept(
                        response -> {
                            Classification classification = parseClassification(response);
                            context.getLog()
                                    .info("Classified '{}' as: {}", truncate(msg.message), classification);
                            msg.reply(classification);
                        })
                .exceptionally(
                        error -> {
                            context.getLog().error("Classification failed: {}", error.getMessage());
                            msg.reply(Classification.UNKNOWN);
                            return null;
                        });

        return Behaviors.same();
    }

    private Classification parseClassification(String response) {
        String normalized = response.trim().toUpperCase();
        try {
            return Classification.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try to extract classification from response
            for (Classification c : Classification.values()) {
                if (normalized.contains(c.name())) {
                    return c;
                }
            }
            return Classification.UNKNOWN;
        }
    }

    private String truncate(String text) {
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
