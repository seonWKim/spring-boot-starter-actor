package io.github.seonwkim.example.ai.actor;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.example.ai.client.LLMClient;
import io.github.seonwkim.example.ai.model.Sentiment;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Agent responsible for analyzing sentiment/emotion in user messages.
 * Uses LLM to determine if message is POSITIVE, NEUTRAL, or NEGATIVE.
 */
@Component
public class SentimentAgent implements SpringActor<SentimentAgent.Command> {

    private final LLMClient llmClient;

    public SentimentAgent(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public interface Command {}

    /**
     * Analyze sentiment of a message
     */
    public static class AnalyzeSentiment extends AskCommand<Sentiment> implements Command {
        public final String message;

        public AnalyzeSentiment(String message) {
            this.message = message;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new SentimentBehavior(ctx, llmClient))
                .onMessage(AnalyzeSentiment.class, SentimentBehavior::handleAnalyze)
                .build();
    }

    private static class SentimentBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final LLMClient llmClient;

        SentimentBehavior(SpringBehaviorContext<Command> ctx, LLMClient llmClient) {
            this.ctx = ctx;
            this.llmClient = llmClient;
        }

        private Behavior<Command> handleAnalyze(
                AnalyzeSentiment msg) {

            ctx.getLog().debug("Analyzing sentiment: {}", msg.message);

            String systemPrompt =
                    """
                You are a sentiment analyzer for customer support messages. Analyze the emotional tone and respond with ONE of these:
                - POSITIVE: Happy, satisfied, grateful, pleased
                - NEUTRAL: Informational, matter-of-fact, no strong emotion
                - NEGATIVE: Frustrated, angry, disappointed, upset

                Respond with ONLY the sentiment category, nothing else.
                """;

            llmClient
                    .chat(systemPrompt, msg.message)
                    .thenAccept(
                            response -> {
                                Sentiment sentiment = parseSentiment(response);
                                ctx.getLog()
                                        .info(
                                                "Analyzed sentiment as: {} for '{}'",
                                                sentiment,
                                                truncate(msg.message));
                                msg.reply(sentiment);
                            })
                    .exceptionally(
                            error -> {
                                ctx.getLog().error("Sentiment analysis failed: {}", error.getMessage());
                                msg.reply(Sentiment.UNKNOWN);
                                return null;
                            });

            return Behaviors.same();
        }

        private Sentiment parseSentiment(String response) {
            String normalized = response.trim().toUpperCase();
            try {
                return Sentiment.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                // Try to extract sentiment from response
                for (Sentiment s : Sentiment.values()) {
                    if (normalized.contains(s.name())) {
                        return s;
                    }
                }
                return Sentiment.UNKNOWN;
            }
        }

        private String truncate(String text) {
            return text.length() > 50 ? text.substring(0, 50) + "..." : text;
        }
    }
}
