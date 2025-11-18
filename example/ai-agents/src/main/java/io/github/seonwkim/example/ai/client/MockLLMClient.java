package io.github.seonwkim.example.ai.client;

import io.github.seonwkim.example.ai.config.AIConfiguration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock LLM client that provides simulated responses.
 * Used when OpenAI API key is not configured.
 * Provides realistic-looking responses based on keyword matching.
 */
public class MockLLMClient implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(MockLLMClient.class);
    private final AIConfiguration aiConfig;

    public MockLLMClient(AIConfiguration aiConfig) {
        this.aiConfig = aiConfig;
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage) {
        logger.debug("[MOCK LLM] System: {}", systemPrompt);
        logger.debug("[MOCK LLM] User: {}", userMessage);

        // Simulate small delay like a real API
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        Thread.sleep(200); // 200ms simulated latency
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    String response = generateMockResponse(systemPrompt, userMessage);
                    logger.debug("[MOCK LLM] Response: {}", response);
                    return response;
                });
    }

    @Override
    public boolean isMock() {
        return true;
    }

    /**
     * Generate mock responses based on keywords in the message.
     * This simulates different agent behaviors.
     */
    private String generateMockResponse(String systemPrompt, String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        // Classification agent responses
        if (systemPrompt.contains("classify") || systemPrompt.contains("intent")) {
            if (containsAny(lowerMessage, "pay", "payment", "bill", "charge", "refund", "cost")) {
                return "BILLING";
            } else if (containsAny(
                    lowerMessage, "error", "bug", "crash", "not work", "broken", "issue")) {
                return "TECHNICAL";
            } else if (containsAny(lowerMessage, "how to", "how do", "what is", "guide", "help")) {
                return "FAQ";
            } else if (containsAny(
                    lowerMessage,
                    "urgent",
                    "emergency",
                    "immediately",
                    "asap",
                    "critical",
                    "angry",
                    "frustrated")) {
                return "ESCALATION";
            } else {
                return "GENERAL";
            }
        }

        // Sentiment analysis agent responses
        if (systemPrompt.contains("sentiment") || systemPrompt.contains("emotion")) {
            if (containsAny(
                    lowerMessage,
                    "angry",
                    "frustrated",
                    "terrible",
                    "worst",
                    "hate",
                    "awful",
                    "horrible")) {
                return "NEGATIVE";
            } else if (containsAny(lowerMessage, "great", "love", "amazing", "excellent", "thank")) {
                return "POSITIVE";
            } else {
                return "NEUTRAL";
            }
        }

        // FAQ agent responses
        if (systemPrompt.contains("FAQ") || systemPrompt.contains("knowledge base")) {
            if (lowerMessage.contains("reset password")) {
                return "To reset your password: 1) Go to login page 2) Click 'Forgot Password' 3)"
                        + " Check your email for reset link. Is there anything else I can help with?";
            } else if (lowerMessage.contains("how to") || lowerMessage.contains("how do")) {
                return "Based on our documentation, here's how to do that: [Mock step-by-step"
                        + " instructions]. Would you like more details on any specific step?";
            } else {
                return "I found some relevant information in our knowledge base. [Mock FAQ answer"
                        + " here]. Does this answer your question?";
            }
        }

        // Escalation agent responses
        if (systemPrompt.contains("escalat") || systemPrompt.contains("human agent")) {
            return "I've created a high-priority ticket (#MOCK-" + System.currentTimeMillis() % 10000
                    + ") and notified our support team. A human agent will contact you within 15"
                    + " minutes.";
        }

        // Default response
        return "I understand your question about: \""
                + truncate(userMessage, 50)
                + "\". [Mock AI response generated]. Can I help you with anything else?";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
