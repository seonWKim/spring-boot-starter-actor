# AI Agents - Multi-Agent Customer Support System

A demonstration of building a production-grade AI customer support system using **spring-boot-starter-actor** with real/mock LLM integration and tiered rate limiting.

## 🎯 What This Demonstrates

- **Multi-Agent Architecture**: Specialized actors for classification, sentiment analysis, FAQ, and escalation
- **Sharded Actor Sessions**: One actor per customer session, distributed across cluster nodes
- **Intelligent Rate Limiting**: Token bucket algorithm with per-tier limits (FREE, PREMIUM, ENTERPRISE)
- **Flexible LLM Integration**: Automatically uses Mock LLM (no API key) or Real OpenAI (with API key)
- **Async Workflow Coordination**: Parallel agent execution for classification + sentiment analysis
- **Production Patterns**: Supervision, error handling, metrics, and proper logging

## 🏗️ Architecture

```
Customer Request
      ↓
┌─────────────────────────────────────────────┐
│  CustomerSessionActor (Sharded)             │
│  - Rate limiting (token bucket)             │
│  - Conversation history                     │
│  - Agent orchestration                      │
└─────────────────────────────────────────────┘
      ↓
   [Check Rate Limit]
      ↓
   [Parallel Execution]
      ├──→ ClassifierAgent  → Classification
      └──→ SentimentAgent   → Sentiment
      ↓
   [Route Based on Analysis]
      ├──→ FAQAgent         → Answer question
      ├──→ EscalationAgent  → Create ticket
      └──→ TechnicalAgent   → (future)
```

## 🚀 Quick Start

### Prerequisites
- Java 11+
- Gradle (or use included wrapper)
- **Optional**: OpenAI API key for real LLM integration

### Run with Mock LLM (No API Key Needed)

```bash
# Start the application
./gradlew :example:ai-agents:bootRun

# The application will automatically use Mock LLM
# Look for this log message:
# ⚠️  OpenAI API key not configured. Using MOCK LLM client with simulated responses.
```

### Run with Real OpenAI

```bash
# Set your API key
export OPENAI_API_KEY=sk-your-actual-key-here

# Start the application
./gradlew :example:ai-agents:bootRun

# You should see:
# ✅ OpenAI API key configured. Using REAL OpenAI client.
```

## 📡 API Usage

### Send a Support Message

```bash
curl -X POST http://localhost:8080/api/support/message \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user123" \
  -H "X-User-Tier: FREE" \
  -d '{
    "message": "How do I reset my password?"
  }'
```

**Response:**
```json
{
  "message": "To reset your password: 1) Go to login page 2) Click 'Forgot Password' 3) Check your email for reset link. Is there anything else I can help with?",
  "rateLimited": false,
  "dailyUsage": 1,
  "dailyLimit": 50,
  "classification": "FAQ",
  "sentiment": "NEUTRAL"
}
```

### Get Session Statistics

```bash
curl "http://localhost:8080/api/support/stats?userId=user123&tier=FREE"
```

**Response:**
```json
{
  "sessionId": "user123:free",
  "userTier": "FREE",
  "messagesProcessed": 5,
  "dailyUsage": 5,
  "dailyLimit": 50,
  "availableTokens": 2,
  "tokenCapacity": 2,
  "recentHistory": [
    {
      "role": "user",
      "content": "How do I reset my password?",
      "timestamp": "2025-01-19T12:00:00Z",
      "classification": "FAQ",
      "sentiment": "NEUTRAL"
    }
  ],
  "createdAt": "2025-01-19T11:00:00Z"
}
```

## 🎭 User Tiers & Rate Limits

| Tier       | Requests/Minute | Burst Capacity | Daily Limit |
|------------|-----------------|----------------|-------------|
| FREE       | 5               | 2              | 50          |
| PREMIUM    | 20              | 5              | 500         |
| ENTERPRISE | 100             | 20             | Unlimited   |

### Testing Different Tiers

```bash
# FREE tier (limited)
curl -X POST http://localhost:8080/api/support/message \
  -H "X-User-Tier: FREE" \
  -d '{"message": "Hello"}'

# PREMIUM tier (more generous)
curl -X POST http://localhost:8080/api/support/message \
  -H "X-User-Tier: PREMIUM" \
  -d '{"message": "Hello"}'

# ENTERPRISE tier (unlimited daily)
curl -X POST http://localhost:8080/api/support/message \
  -H "X-User-Tier: ENTERPRISE" \
  -d '{"message": "Hello"}'
```

### Trigger Rate Limiting

```bash
# Rapid-fire 10 requests as FREE user (will hit limit)
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/support/message \
    -H "Content-Type: application/json" \
    -H "X-User-Id: test-user" \
    -H "X-User-Tier: FREE" \
    -d "{\"message\": \"Request $i\"}"
  echo ""
done
```

**Rate Limited Response (HTTP 429):**
```json
{
  "message": "⚠️ Rate limit exceeded. Please try again in a moment. (Tier: FREE, 5 req/min, daily usage: 6/50)",
  "rateLimited": true,
  "dailyUsage": 6,
  "dailyLimit": 50,
  "classification": null,
  "sentiment": null
}
```

## 🧪 Example Scenarios

### 1. FAQ Question
```bash
curl -X POST http://localhost:8080/api/support/message \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user1" \
  -d '{"message": "How do I reset my password?"}'

# Classification: FAQ
# Agent: FAQAgent
# Response: Step-by-step password reset instructions
```

### 2. Frustrated Customer (Escalation)
```bash
curl -X POST http://localhost:8080/api/support/message \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user2" \
  -d '{"message": "This is terrible! My payment failed and I am very angry!"}'

# Classification: BILLING (or ESCALATION)
# Sentiment: NEGATIVE
# Agent: EscalationAgent
# Response: Ticket created, human agent notified
```

### 3. Technical Issue
```bash
curl -X POST http://localhost:8080/api/support/message \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user3" \
  -d '{"message": "The app keeps crashing when I try to login"}'

# Classification: TECHNICAL
# Sentiment: NEUTRAL
# Agent: FAQAgent (currently, can route to TechnicalAgent in future)
```

## ⚙️ Configuration

### application.yml

```yaml
app:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:not-set}  # Auto-selects Mock if not set
      model: gpt-3.5-turbo
      temperature: 0.7
      max-tokens: 500

    rate-limit:
      free:
        requests-per-minute: 5
        burst-capacity: 2
        daily-limit: 50
```

### Environment Variables

```bash
# OpenAI API Key (optional, uses Mock LLM if not set)
export OPENAI_API_KEY=sk-your-key

# Server Port (optional)
export SERVER_PORT=8080
```

## 🔍 Key Components

### Agents (Actors)

- **ClassifierAgent**: Determines message intent (FAQ, TECHNICAL, BILLING, etc.)
- **SentimentAgent**: Analyzes emotional tone (POSITIVE, NEUTRAL, NEGATIVE)
- **FAQAgent**: Answers common questions using LLM with knowledge base
- **EscalationAgent**: Creates support tickets for human intervention
- **CustomerSessionActor**: Orchestrates agents, manages state and rate limits

### LLM Clients

- **LLMClient** (Interface): Abstraction for LLM integration
- **MockLLMClient**: Keyword-based simulated responses (no API needed)
- **OpenAIClient**: Real OpenAI API integration via WebClient

### Rate Limiting

- **TokenBucket**: Time-based token refill algorithm
- Per-user session state (actor-safe, no locks needed)
- Configurable per-tier limits

## 📊 Monitoring

### Logs

```bash
# Watch logs in real-time
tail -f logs/application.log

# Key log patterns:
# - "Created session: user123:free with tier: FREE"
# - "Classified 'How to...' as: FAQ"
# - "Analyzed sentiment as: NEUTRAL"
# - "Rate limit exceeded for session: user123:free"
```

### Health Check

```bash
curl http://localhost:8080/api/support/health

# Response: "AI Support System is running"
```

## 🏗️ Extending the Example

### Add a New Agent

```java
@Component
public class BillingAgent implements SpringActor<BillingAgent.Command> {
    private final LLMClient llmClient;
    private final BillingService billingService;

    // Implement agent logic
}
```

### Route to New Agent

In `CustomerSessionActor.routeToAgent()`:
```java
if (analysis.classification == Classification.BILLING) {
    return handleBilling(msg, analysis);
}
```

### Add Event Sourcing

Extend `CustomerSessionActor` to persist events:
```java
// Save events
history.add(new MessageReceived(msg.message));
eventRepository.save(new MessageEvent(...));

// Replay on actor restart
eventRepository.findBySessionId(sessionId)
    .forEach(event -> apply(event));
```

## 🎓 Learning Points

### Actor Model Benefits

1. **Isolation**: Each session is an independent actor
2. **Concurrency**: Parallel agent execution (Classifier + Sentiment)
3. **Fault Tolerance**: Agent failures don't crash the session
4. **Scalability**: Sharded actors distribute across cluster nodes
5. **State Management**: No shared state, no locks needed

### AI-Era Patterns

1. **Multi-Agent Orchestration**: Coordinate multiple AI agents
2. **Rate Limiting**: Control costs with token budgets
3. **Async LLM Calls**: Non-blocking API integration
4. **Graceful Degradation**: Mock LLM when API unavailable
5. **Observability**: Track usage, sentiment, classification

## 🐛 Troubleshooting

### "Rate limit exceeded" immediately

- Token bucket may need time to refill
- Check `burst-capacity` in configuration
- Reset session by using different `X-User-Id`

### Mock LLM not responding correctly

- Check logs for classification/sentiment debug messages
- Mock uses keyword matching, try clearer phrasing
- Consider using real OpenAI for better results

### "Connection refused" errors

- Ensure application is running: `./gradlew :example:ai-agents:bootRun`
- Check port 8080 is not in use: `lsof -i :8080`
- Verify application.yml server port configuration

## 📚 Further Reading

- [Spring Boot Starter Actor Documentation](../../README.md)
- [Actor Model Concepts](https://doc.akka.io/docs/akka/current/typed/guide/actors-intro.html)
- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)

## 📄 License

This example is part of spring-boot-starter-actor and follows the same Apache 2.0 license.
