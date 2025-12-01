package io.github.seonwkim.example.ai.config;

import io.github.seonwkim.example.ai.client.LLMClient;
import io.github.seonwkim.example.ai.client.MockLLMClient;
import io.github.seonwkim.example.ai.client.OpenAIClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * Creates appropriate LLM client based on API key configuration.
     * Returns MockLLMClient when API key is not configured or equals "not-set".
     * Returns OpenAIClient when API key is properly configured.
     */
    @Bean
    public LLMClient llmClient(AIConfiguration aiConfig, WebClient.Builder webClientBuilder) {
        AIConfiguration.OpenAIConfig config = aiConfig.getOpenai();

        // Check if API key is configured and valid
        if (!config.isConfigured()) {
            logger.warn(
                    "⚠️  OpenAI API key not configured. Using MOCK LLM client with simulated responses.");
            logger.warn("   To use real OpenAI API, set app.ai.openai.api-key in application.yml");
            logger.warn(
                    "   or set environment variable: export OPENAI_API_KEY=sk-your-key-here\n");
            return new MockLLMClient(aiConfig);
        }

        logger.info("✅ OpenAI API key configured. Using REAL OpenAI client.");
        logger.info("   Model: {}", config.getModel());
        logger.info("   Base URL: {}", config.getBaseUrl());
        logger.info("   Timeout: {}\n", config.getTimeout());

        return new OpenAIClient(aiConfig, webClientBuilder);
    }
}
