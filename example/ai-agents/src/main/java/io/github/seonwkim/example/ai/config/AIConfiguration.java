package io.github.seonwkim.example.ai.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI agents system.
 * Maps to 'app.ai' in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AIConfiguration {

    private OpenAIConfig openai = new OpenAIConfig();
    private Map<String, RateLimitConfig> rateLimit = Map.of();
    private AgentConfig agents = new AgentConfig();
    private FallbackConfig fallback = new FallbackConfig();

    public OpenAIConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAIConfig openai) {
        this.openai = openai;
    }

    public Map<String, RateLimitConfig> getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Map<String, RateLimitConfig> rateLimit) {
        this.rateLimit = rateLimit;
    }

    public AgentConfig getAgents() {
        return agents;
    }

    public void setAgents(AgentConfig agents) {
        this.agents = agents;
    }

    public FallbackConfig getFallback() {
        return fallback;
    }

    public void setFallback(FallbackConfig fallback) {
        this.fallback = fallback;
    }

    /** OpenAI API configuration */
    public static class OpenAIConfig {
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-3.5-turbo";
        private Double temperature = 0.7;
        private Integer maxTokens = 500;
        private Duration timeout = Duration.ofSeconds(30);

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /** Check if OpenAI API key is configured */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("not-set");
        }
    }

    /** Rate limiting configuration per user tier */
    public static class RateLimitConfig {
        private Integer requestsPerMinute = 10;
        private Integer burstCapacity = 5;
        private Integer dailyLimit = 100;

        public Integer getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(Integer requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public Integer getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(Integer burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public Integer getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(Integer dailyLimit) {
            this.dailyLimit = dailyLimit;
        }
    }

    /** Agent-specific configuration */
    public static class AgentConfig {
        private AgentPoolConfig classifier = new AgentPoolConfig();
        private AgentPoolConfig sentiment = new AgentPoolConfig();
        private AgentPoolConfig faq = new AgentPoolConfig();
        private AgentPoolConfig escalation = new AgentPoolConfig();

        public AgentPoolConfig getClassifier() {
            return classifier;
        }

        public void setClassifier(AgentPoolConfig classifier) {
            this.classifier = classifier;
        }

        public AgentPoolConfig getSentiment() {
            return sentiment;
        }

        public void setSentiment(AgentPoolConfig sentiment) {
            this.sentiment = sentiment;
        }

        public AgentPoolConfig getFaq() {
            return faq;
        }

        public void setFaq(AgentPoolConfig faq) {
            this.faq = faq;
        }

        public AgentPoolConfig getEscalation() {
            return escalation;
        }

        public void setEscalation(AgentPoolConfig escalation) {
            this.escalation = escalation;
        }

        public static class AgentPoolConfig {
            private Integer poolSize = 5;
            private Duration timeout = Duration.ofSeconds(10);

            public Integer getPoolSize() {
                return poolSize;
            }

            public void setPoolSize(Integer poolSize) {
                this.poolSize = poolSize;
            }

            public Duration getTimeout() {
                return timeout;
            }

            public void setTimeout(Duration timeout) {
                this.timeout = timeout;
            }
        }
    }

    /** Fallback messages configuration */
    public static class FallbackConfig {
        private String rateLimitMessage =
                "Rate limit exceeded. Please try again in a moment.";
        private String errorMessage =
                "Sorry, I'm having trouble processing your request. Please try again.";
        private String escalationMessage =
                "I'm transferring you to a human agent who can better assist you.";

        public String getRateLimitMessage() {
            return rateLimitMessage;
        }

        public void setRateLimitMessage(String rateLimitMessage) {
            this.rateLimitMessage = rateLimitMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getEscalationMessage() {
            return escalationMessage;
        }

        public void setEscalationMessage(String escalationMessage) {
            this.escalationMessage = escalationMessage;
        }
    }
}
