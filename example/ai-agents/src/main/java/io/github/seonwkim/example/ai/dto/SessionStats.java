package io.github.seonwkim.example.ai.dto;

import io.github.seonwkim.example.ai.model.ConversationMessage;
import java.time.Instant;
import java.util.List;

/**
 * Statistics about a customer support session.
 * Includes rate limit info and conversation history.
 */
public record SessionStats(
        String sessionId,
        String userTier,
        int messagesProcessed,
        int dailyUsage,
        int dailyLimit,
        int availableTokens,
        int tokenCapacity,
        List<ConversationMessage> recentHistory,
        Instant createdAt) {}
