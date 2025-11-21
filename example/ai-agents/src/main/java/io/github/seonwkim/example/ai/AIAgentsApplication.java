package io.github.seonwkim.example.ai;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI Agents Example Application
 *
 * <p>Demonstrates a multi-agent AI customer support system using spring-boot-starter-actor.
 *
 * <p>Features:
 * - Sharded actor sessions (one per customer)
 * - Rate limiting (token bucket per user tier)
 * - Multiple specialized agents (Classifier, Sentiment, FAQ, Escalation)
 * - Real or Mock LLM integration (auto-selected based on API key)
 * - Parallel agent execution for performance
 *
 * <p>Usage:
 * <pre>
 * # Start with Mock LLM (no API key needed)
 * ./gradlew :example:ai-agents:bootRun
 *
 * # Start with Real OpenAI
 * export OPENAI_API_KEY=sk-your-key
 * ./gradlew :example:ai-agents:bootRun
 *
 * # Test the API
 * curl -X POST http://localhost:8080/api/support/message \
 *   -H "Content-Type: application/json" \
 *   -H "X-User-Id: user123" \
 *   -H "X-User-Tier: FREE" \
 *   -d '{"message": "How do I reset my password?"}'
 * </pre>
 */
@SpringBootApplication
@EnableActorSupport
@EnableConfigurationProperties
public class AIAgentsApplication {

    public static void main(String[] args) {
        System.out.println(
                """
                ╔═══════════════════════════════════════════════════════════╗
                ║  AI Agents - Multi-Agent Customer Support System          ║
                ║  Powered by spring-boot-starter-actor                     ║
                ╚═══════════════════════════════════════════════════════════╝
                """);

        SpringApplication.run(AIAgentsApplication.class, args);

        System.out.println(
                """

                ✅ Application started successfully!

                📡 API Endpoints:
                   POST http://localhost:8080/api/support/message
                   GET  http://localhost:8080/api/support/stats
                   GET  http://localhost:8080/api/support/health

                💡 Example Request:
                   curl -X POST http://localhost:8080/api/support/message \\
                     -H "Content-Type: application/json" \\
                     -H "X-User-Id: user123" \\
                     -H "X-User-Tier: FREE" \\
                     -d '{"message": "How do I reset my password?"}'

                📊 User Tiers:
                   FREE      - 5 req/min,  50/day
                   PREMIUM   - 20 req/min, 500/day
                   ENTERPRISE - 100 req/min, unlimited

                🔑 LLM Mode:
                   Check startup logs to see if using Mock or Real OpenAI

                """);
    }
}
