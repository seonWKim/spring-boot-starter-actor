package io.github.seonwkim.example.ai.dto;

import io.github.seonwkim.example.ai.model.Classification;
import io.github.seonwkim.example.ai.model.Sentiment;

/**
 * Response from the customer support system.
 * Returned after processing a customer message.
 */
public record SupportResponse(
        String message,
        boolean rateLimited,
        int dailyUsage,
        int dailyLimit,
        Classification classification,
        Sentiment sentiment) {

    public SupportResponse(String message) {
        this(message, false, 0, 0, null, null);
    }
}
