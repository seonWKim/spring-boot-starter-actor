package io.github.seonwkim.example.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.example.ai.config.AIConfiguration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Real OpenAI API client implementation.
 * Makes actual HTTP calls to OpenAI's chat completion API.
 */
public class OpenAIClient implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    private final WebClient webClient;
    private final AIConfiguration.OpenAIConfig config;

    public OpenAIClient(AIConfiguration aiConfig, WebClient.Builder webClientBuilder) {
        this.config = aiConfig.getOpenai();
        this.webClient =
                webClientBuilder
                        .baseUrl(config.getBaseUrl())
                        .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                        .defaultHeader("Content-Type", "application/json")
                        .build();
    }

    @Override
    public CompletableFuture<String> chat(String systemPrompt, String userMessage) {
        ChatRequest request =
                new ChatRequest(
                        config.getModel(),
                        List.of(
                                new Message("system", systemPrompt),
                                new Message("user", userMessage)),
                        config.getTemperature(),
                        config.getMaxTokens());

        logger.debug("[OpenAI] Sending request: model={}, user={}", config.getModel(), userMessage);

        return webClient
                .post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .timeout(config.getTimeout())
                .onErrorResume(
                        e -> {
                            logger.error("[OpenAI] API call failed: {}", e.getMessage());
                            return Mono.error(
                                    new RuntimeException("OpenAI API call failed: " + e.getMessage(), e));
                        })
                .map(
                        response -> {
                            String content = response.choices().get(0).message().content();
                            logger.debug("[OpenAI] Response received: tokens={}", response.usage().totalTokens());
                            logger.trace("[OpenAI] Content: {}", content);
                            return content;
                        })
                .toFuture();
    }

    @Override
    public boolean isMock() {
        return false;
    }

    // DTOs for OpenAI API
    record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens) {}

    record Message(String role, String content) {}

    record ChatResponse(List<Choice> choices, Usage usage) {}

    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {}

    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens) {}
}
