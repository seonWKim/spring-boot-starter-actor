package io.github.seonwkim.example.ai.controller;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.ai.actor.CustomerSessionActor;
import io.github.seonwkim.example.ai.dto.MessageRequest;
import io.github.seonwkim.example.ai.dto.SessionStats;
import io.github.seonwkim.example.ai.dto.SupportResponse;
import io.github.seonwkim.example.ai.model.UserTier;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for AI customer support system.
 * Provides endpoints for sending messages and retrieving session stats.
 */
@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SpringActorSystem actorSystem;

    public SupportController(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Send a message to the support system.
     *
     * @param userId User identifier (from header)
     * @param tier User tier for rate limiting (from header, defaults to FREE)
     * @param request Message content
     * @return Support response with classification, sentiment, and answer
     */
    @PostMapping("/message")
    public CompletionStage<ResponseEntity<SupportResponse>> sendMessage(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestHeader(value = "X-User-Tier", defaultValue = "FREE") String tierStr,
            @RequestBody MessageRequest request) {

        UserTier tier = UserTier.fromString(tierStr);
        String sessionId = generateSessionId(userId, tier);

        return actorSystem
                .sharded(CustomerSessionActor.class)
                .withId(sessionId)
                .get()
                .ask(new CustomerSessionActor.HandleMessage(userId, request.getMessage(), tier))
                .withTimeout(Duration.ofSeconds(30))
                .execute()
                .thenApply(
                        response -> {
                            if (response.rateLimited()) {
                                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .header("X-RateLimit-Remaining", "0")
                                        .header("X-RateLimit-Reset", "60")
                                        .header("X-User-Tier", tier.name())
                                        .body(response);
                            }
                            return ResponseEntity.ok()
                                    .header("X-Daily-Usage", String.valueOf(response.dailyUsage()))
                                    .header("X-Daily-Limit", String.valueOf(response.dailyLimit()))
                                    .header("X-User-Tier", tier.name())
                                    .body(response);
                        })
                .toCompletableFuture();
    }

    /**
     * Get statistics for a user session.
     *
     * @param userId User identifier
     * @param tier User tier (defaults to FREE)
     * @return Session statistics including conversation history and rate limit info
     */
    @GetMapping("/stats")
    public CompletionStage<ResponseEntity<SessionStats>> getStats(
            @RequestParam(value = "userId", defaultValue = "anonymous") String userId,
            @RequestParam(value = "tier", defaultValue = "FREE") String tierStr) {

        UserTier tier = UserTier.fromString(tierStr);
        String sessionId = generateSessionId(userId, tier);

        return actorSystem
                .sharded(CustomerSessionActor.class)
                .withId(sessionId)
                .get()
                .ask(new CustomerSessionActor.GetStats())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .thenApply(ResponseEntity::ok)
                .exceptionally(
                        error ->
                                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(null))
                .toCompletableFuture();
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Support System is running");
    }

    /**
     * Generate session ID from user ID and tier.
     * Format: "userId:tier" (e.g., "user123:premium")
     */
    private String generateSessionId(String userId, UserTier tier) {
        return userId + ":" + tier.getConfigKey();
    }
}
