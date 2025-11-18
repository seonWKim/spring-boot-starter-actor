package io.github.seonwkim.example.ai.actor;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.command.AskCommand;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import io.github.seonwkim.example.ai.config.AIConfiguration;
import io.github.seonwkim.example.ai.dto.SessionStats;
import io.github.seonwkim.example.ai.dto.SupportResponse;
import io.github.seonwkim.example.ai.model.Classification;
import io.github.seonwkim.example.ai.model.ConversationMessage;
import io.github.seonwkim.example.ai.model.Sentiment;
import io.github.seonwkim.example.ai.model.UserTier;
import io.github.seonwkim.example.ai.ratelimit.TokenBucket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

/**
 * Sharded actor managing customer support sessions.
 * One actor instance per customer session, distributed across cluster.
 *
 * <p>Responsibilities:
 * - Rate limiting (per-user tier)
 * - Conversation history management
 * - Agent workflow orchestration
 * - Classification → Sentiment → Routing
 */
@Component
public class CustomerSessionActor
        implements SpringShardedActor<CustomerSessionActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "CustomerSession");

    private final AIConfiguration aiConfig;
    private final SpringActorSystem actorSystem;

    public CustomerSessionActor(AIConfiguration aiConfig, SpringActorSystem actorSystem) {
        this.aiConfig = aiConfig;
        this.actorSystem = actorSystem;
    }

    public interface Command extends JsonSerializable {}

    /**
     * Handle a customer message
     */
    public static class HandleMessage extends AskCommand<SupportResponse> implements Command {
        public final String userId;
        public final String message;
        public final UserTier tier;

        public HandleMessage(String userId, String message, UserTier tier) {
            this.userId = userId;
            this.message = message;
            this.tier = tier;
        }
    }

    /**
     * Get session statistics
     */
    public static class GetStats extends AskCommand<SessionStats> implements Command {
        public GetStats() {}
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        String sessionId = ctx.getEntityId();

        // Extract user tier from session ID (format: "userId:tierName")
        UserTier tier = extractTier(sessionId);
        AIConfiguration.RateLimitConfig rateLimitConfig =
                aiConfig.getRateLimit().get(tier.getConfigKey());

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(
                        behaviorCtx ->
                                new SessionBehavior(
                                        behaviorCtx, sessionId, tier, rateLimitConfig, actorSystem))
                .onMessage(HandleMessage.class, SessionBehavior::handleMessage)
                .onMessage(GetStats.class, SessionBehavior::handleGetStats)
                .build();
    }

    private UserTier extractTier(String sessionId) {
        // Session ID format: "userId:tier" (e.g., "user123:premium")
        String[] parts = sessionId.split(":");
        if (parts.length >= 2) {
            return UserTier.fromString(parts[1]);
        }
        return UserTier.FREE; // default
    }

    /**
     * Session state and behavior
     */
    private class SessionBehavior {
        private final io.github.seonwkim.core.SpringBehaviorContext<Command> ctx;
        private final String sessionId;
        private final UserTier userTier;
        private final AIConfiguration.RateLimitConfig rateLimitConfig;
        private final TokenBucket rateLimiter;
        private final List<ConversationMessage> history;
        private final SpringActorSystem actorSystem;
        private final Instant createdAt;

        private int dailyRequestCount;
        private int totalMessagesProcessed;

        SessionBehavior(
                io.github.seonwkim.core.SpringBehaviorContext<Command> ctx,
                String sessionId,
                UserTier tier,
                AIConfiguration.RateLimitConfig config,
                SpringActorSystem actorSystem) {
            this.ctx = ctx;
            this.sessionId = sessionId;
            this.userTier = tier;
            this.rateLimitConfig = config;
            this.rateLimiter =
                    new TokenBucket(
                            config.getRequestsPerMinute(), config.getBurstCapacity());
            this.history = new ArrayList<>();
            this.actorSystem = actorSystem;
            this.createdAt = Instant.now();
            this.dailyRequestCount = 0;
            this.totalMessagesProcessed = 0;

            ctx.getLog()
                    .info(
                            "Created session: {} with tier: {} (limit: {}/min, daily: {})",
                            sessionId,
                            tier,
                            config.getRequestsPerMinute(),
                            config.getDailyLimit());
        }

        Behavior<Command> handleMessage(HandleMessage msg) {
            ctx.getLog().info("Processing message from user: {}", msg.userId);

            // Check rate limits
            if (!checkRateLimit()) {
                String rateLimitMsg =
                        String.format(
                                "%s (Tier: %s, %d req/min, daily usage: %d/%d)",
                                aiConfig.getFallback().getRateLimitMessage(),
                                userTier,
                                rateLimitConfig.getRequestsPerMinute(),
                                dailyRequestCount,
                                rateLimitConfig.getDailyLimit());

                ctx.getLog().warn("Rate limit exceeded for session: {}", sessionId);
                msg.reply(
                        new SupportResponse(
                                rateLimitMsg,
                                true,
                                dailyRequestCount,
                                rateLimitConfig.getDailyLimit(),
                                null,
                                null));
                return Behaviors.same();
            }

            // Store user message
            history.add(new ConversationMessage("user", msg.message));
            totalMessagesProcessed++;

            // Process message through agent workflow
            processMessageAsync(msg);

            return Behaviors.same();
        }

        private boolean checkRateLimit() {
            // Check daily limit
            if (rateLimitConfig.getDailyLimit() > 0
                    && dailyRequestCount >= rateLimitConfig.getDailyLimit()) {
                return false;
            }

            // Check token bucket
            if (!rateLimiter.tryConsume()) {
                return false;
            }

            dailyRequestCount++;
            return true;
        }

        private void processMessageAsync(HandleMessage msg) {
            ctx.getLog().debug("Starting agent workflow for message");

            // Spawn agent actors
            CompletableFuture<Void> agentSetup =
                    CompletableFuture.allOf(
                            actorSystem
                                    .actor(ClassifierAgent.class)
                                    .withId("classifier-1")
                                    .spawn()
                                    .toCompletableFuture(),
                            actorSystem
                                    .actor(SentimentAgent.class)
                                    .withId("sentiment-1")
                                    .spawn()
                                    .toCompletableFuture());

            agentSetup
                    .thenCompose(
                            v -> {
                                // Step 1 & 2: Classify and analyze sentiment in parallel
                                CompletableFuture<Classification> classificationFuture =
                                        actorSystem
                                                .get(ClassifierAgent.class, "classifier-1")
                                                .thenCompose(
                                                        agent ->
                                                                agent.ask(
                                                                                new ClassifierAgent.ClassifyMessage(
                                                                                        msg.message))
                                                                        .withTimeout(Duration.ofSeconds(10))
                                                                        .execute()
                                                                        .toCompletableFuture());

                                CompletableFuture<Sentiment> sentimentFuture =
                                        actorSystem
                                                .get(SentimentAgent.class, "sentiment-1")
                                                .thenCompose(
                                                        agent ->
                                                                agent.ask(
                                                                                new SentimentAgent.AnalyzeSentiment(
                                                                                        msg.message))
                                                                        .withTimeout(Duration.ofSeconds(10))
                                                                        .execute()
                                                                        .toCompletableFuture());

                                return CompletableFuture.allOf(classificationFuture, sentimentFuture)
                                        .thenApply(
                                                ignored -> {
                                                    Classification classification = classificationFuture.join();
                                                    Sentiment sentiment = sentimentFuture.join();
                                                    return new AnalysisResult(classification, sentiment);
                                                });
                            })
                    .thenCompose(analysis -> routeToAgent(msg, analysis))
                    .thenAccept(
                            response -> {
                                // Store assistant response
                                history.add(
                                        new ConversationMessage(
                                                "assistant",
                                                response.message(),
                                                response.classification(),
                                                response.sentiment()));

                                ctx.getLog().info("Message processed successfully");
                                msg.reply(response);
                            })
                    .exceptionally(
                            error -> {
                                ctx.getLog()
                                        .error("Message processing failed: {}", error.getMessage(), error);
                                msg.reply(
                                        new SupportResponse(
                                                aiConfig.getFallback().getErrorMessage(),
                                                false,
                                                dailyRequestCount,
                                                rateLimitConfig.getDailyLimit(),
                                                Classification.UNKNOWN,
                                                Sentiment.UNKNOWN));
                                return null;
                            });
        }

        private CompletableFuture<SupportResponse> routeToAgent(
                HandleMessage msg, AnalysisResult analysis) {
            ctx.getLog()
                    .info(
                            "Routing: classification={}, sentiment={}",
                            analysis.classification,
                            analysis.sentiment);

            // Route based on classification and sentiment
            if (analysis.classification == Classification.ESCALATION
                    || analysis.sentiment == Sentiment.NEGATIVE) {
                return handleEscalation(msg, analysis);
            } else if (analysis.classification == Classification.FAQ
                    || analysis.classification == Classification.GENERAL) {
                return handleFAQ(msg, analysis);
            } else {
                // TECHNICAL, BILLING, etc. - for now, use FAQ agent
                return handleFAQ(msg, analysis);
            }
        }

        private CompletableFuture<SupportResponse> handleFAQ(
                HandleMessage msg, AnalysisResult analysis) {
            return actorSystem
                    .actor(FAQAgent.class)
                    .withId("faq-1")
                    .spawn()
                    .thenCompose(
                            faqAgent ->
                                    faqAgent
                                            .ask(new FAQAgent.AnswerQuestion(msg.message))
                                            .withTimeout(Duration.ofSeconds(15))
                                            .execute())
                    .thenApply(
                            answer ->
                                    new SupportResponse(
                                            answer,
                                            false,
                                            dailyRequestCount,
                                            rateLimitConfig.getDailyLimit(),
                                            analysis.classification,
                                            analysis.sentiment))
                    .toCompletableFuture();
        }

        private CompletableFuture<SupportResponse> handleEscalation(
                HandleMessage msg, AnalysisResult analysis) {
            return actorSystem
                    .actor(EscalationAgent.class)
                    .withId("escalation-1")
                    .spawn()
                    .thenCompose(
                            escalationAgent ->
                                    escalationAgent
                                            .ask(
                                                    new EscalationAgent.EscalateIssue(
                                                            msg.userId, msg.message, analysis.sentiment))
                                            .withTimeout(Duration.ofSeconds(10))
                                            .execute())
                    .thenApply(
                            escalationResponse ->
                                    new SupportResponse(
                                            escalationResponse,
                                            false,
                                            dailyRequestCount,
                                            rateLimitConfig.getDailyLimit(),
                                            analysis.classification,
                                            analysis.sentiment))
                    .toCompletableFuture();
        }

        Behavior<Command> handleGetStats(GetStats msg) {
            SessionStats stats =
                    new SessionStats(
                            sessionId,
                            userTier.name(),
                            totalMessagesProcessed,
                            dailyRequestCount,
                            rateLimitConfig.getDailyLimit(),
                            rateLimiter.availableTokens(),
                            rateLimitConfig.getBurstCapacity(),
                            new ArrayList<>(history.subList(Math.max(0, history.size() - 10), history.size())),
                            createdAt);

            msg.reply(stats);
            return Behaviors.same();
        }
    }

    private record AnalysisResult(Classification classification, Sentiment sentiment) {}
}
