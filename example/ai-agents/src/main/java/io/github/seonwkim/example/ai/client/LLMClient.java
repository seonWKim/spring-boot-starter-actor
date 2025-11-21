package io.github.seonwkim.example.ai.client;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for LLM (Large Language Model) client.
 * Implementations can be real (OpenAI) or mock (for testing/demo).
 */
public interface LLMClient {

    /**
     * Send a chat completion request with system and user messages.
     *
     * @param systemPrompt The system message that sets the behavior/role
     * @param userMessage The user's input message
     * @return CompletableFuture with the LLM's response
     */
    CompletableFuture<String> chat(String systemPrompt, String userMessage);

    /**
     * Send a simple chat request with just a user message.
     *
     * @param userMessage The user's input message
     * @return CompletableFuture with the LLM's response
     */
    default CompletableFuture<String> chat(String userMessage) {
        return chat("You are a helpful AI assistant.", userMessage);
    }

    /**
     * Check if this is a mock client (for testing) or real client.
     *
     * @return true if mock, false if real
     */
    boolean isMock();
}
