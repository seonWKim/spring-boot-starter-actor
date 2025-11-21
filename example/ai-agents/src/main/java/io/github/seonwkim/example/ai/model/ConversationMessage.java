package io.github.seonwkim.example.ai.model;

import java.time.Instant;

/**
 * Represents a single message in a conversation.
 * Used to track conversation history within CustomerSessionActor.
 */
public class ConversationMessage {

    private final String role; // "user" or "assistant"
    private final String content;
    private final Instant timestamp;
    private final Classification classification;
    private final Sentiment sentiment;

    public ConversationMessage(
            String role,
            String content,
            Classification classification,
            Sentiment sentiment) {
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
        this.classification = classification;
        this.sentiment = sentiment;
    }

    public ConversationMessage(String role, String content) {
        this(role, content, null, null);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Classification getClassification() {
        return classification;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", timestamp, role, content);
    }
}
