package io.github.seonwkim.example.ai.actor;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.example.ai.client.LLMClient;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Agent responsible for answering frequently asked questions.
 * Uses LLM with FAQ knowledge base context to provide answers.
 */
@Component
public class FAQAgent implements SpringActor<FAQAgent.Command> {

    private final LLMClient llmClient;

    public FAQAgent(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public interface Command {}

    /**
     * Answer an FAQ question
     */
    public static class AnswerQuestion extends AskCommand<String> implements Command {
        public final String question;

        public AnswerQuestion(String question) {
            this.question = question;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new FAQBehavior(ctx, llmClient))
                .onMessage(AnswerQuestion.class, FAQBehavior::handleAnswer)
                .build();
    }

    private static class FAQBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final LLMClient llmClient;

        FAQBehavior(SpringBehaviorContext<Command> ctx, LLMClient llmClient) {
            this.ctx = ctx;
            this.llmClient = llmClient;
        }

        private org.apache.pekko.actor.typed.Behavior<Command> handleAnswer(
                AnswerQuestion msg) {

            ctx.getLog().info("FAQ Agent answering: {}", truncate(msg.question));

            String systemPrompt =
                    """
                You are a helpful customer support agent with access to the FAQ knowledge base.

                Common FAQs:
                - Password reset: Go to login page → Click "Forgot Password" → Check email for reset link
                - Account creation: Click "Sign Up" → Fill in details → Verify email → Login
                - Billing cycle: Subscriptions renew monthly on the same date you signed up
                - Refund policy: Full refund within 30 days, contact support@example.com
                - API rate limits: Free tier: 100 req/day, Premium: 10k req/day, Enterprise: unlimited

                Provide a helpful, concise answer (2-3 sentences). Be friendly and offer to help further.
                """;

            llmClient
                    .chat(systemPrompt, msg.question)
                    .thenAccept(
                            response -> {
                                ctx.getLog().debug("FAQ response generated: {}", truncate(response));
                                msg.reply(response);
                            })
                    .exceptionally(
                            error -> {
                                ctx.getLog().error("FAQ agent failed: {}", error.getMessage());
                                msg.reply(
                                        "I apologize, but I'm having trouble accessing our FAQ database right now. "
                                                + "Please try again in a moment or contact support@example.com.");
                                return null;
                            });

            return Behaviors.same();
        }

        private String truncate(String text) {
            return text.length() > 50 ? text.substring(0, 50) + "..." : text;
        }
    }
}
