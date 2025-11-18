package io.github.seonwkim.example.ai.config;

import io.github.seonwkim.example.ai.client.LLMClient;
import io.github.seonwkim.example.ai.client.MockLLMClient;
import io.github.seonwkim.example.ai.client.OpenAIClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for LLM client.
 * Automatically selects between Mock and Real OpenAI client based on API key configuration.
 */
@Configuration
public class LLMClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(LLMClientConfiguration.class);

    /**
     * Creates OpenAI client when API key is configured.
     * Activated when: app.ai.openai.api-key is set and not equal to "not-set"
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.ai.openai",
            name = "api-key",
            havingValue = "not-set",
            matchIfMissing = true)
    public LLMClient mockLLMClient(AIConfiguration aiConfig) {
        logger.warn(
                "⚠️  OpenAI API key not configured. Using MOCK LLM client with simulated responses.");
        logger.warn("   To use real OpenAI API, set app.ai.openai.api-key in application.yml");
        logger.warn(
                "   or set environment variable: export OPENAI_API_KEY=sk-your-key-here\n");
        return new MockLLMClient(aiConfig);
    }

    /**
     * Creates real OpenAI client when API key is properly configured.
     * This bean will NOT be created when api-key is "not-set" due to the ConditionalOnProperty
     * on mockLLMClient
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.ai.openai", name = "api-key")
    public LLMClient realLLMClient(AIConfiguration aiConfig, WebClient.Builder webClientBuilder) {
        AIConfiguration.OpenAIConfig config = aiConfig.getOpenai();

        // Additional check to ensure we don't use "not-set" as a real API key
        if (!config.isConfigured()) {
            logger.warn(
                    "⚠️  OpenAI API key is set but invalid. Falling back to MOCK LLM client.");
            return new MockLLMClient(aiConfig);
        }

        logger.info("✅ OpenAI API key configured. Using REAL OpenAI client.");
        logger.info("   Model: {}", config.getModel());
        logger.info("   Base URL: {}", config.getBaseUrl());
        logger.info("   Timeout: {}\n", config.getTimeout());

        return new OpenAIClient(aiConfig, webClientBuilder);
    }
}
