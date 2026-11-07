package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.example.agent.ratelimit.RateLimiterActor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "agent.calls-per-second=" + RateLimiterTest.LIMIT)
@DirtiesContext
class RateLimiterTest {

    static final int LIMIT = 5;
    private static final long SECOND_NANOS = TimeUnit.SECONDS.toNanos(1);

    @Autowired
    private AgentRuntime runtime;

    @Autowired
    private AgentService agents;

    @Test
    void neverGrantsMoreThanTheLimitInAnyOneSecondWindow() throws Exception {
        List<CompletableFuture<RateLimiterActor.Permit>> requests = new ArrayList<>();
        for (int i = 0; i < LIMIT * 4; i++) {
            requests.add(runtime.rateLimiter()
                    .ask(new RateLimiterActor.Acquire())
                    .withTimeout(Duration.ofSeconds(15))
                    .execute()
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(requests.toArray(new CompletableFuture[0])).get(20, TimeUnit.SECONDS);

        long[] granted = requests.stream()
                .mapToLong(request -> request.join().grantedAtNanos())
                .sorted()
                .toArray();
        // Any LIMIT + 1 consecutive permits must span at least one second.
        for (int i = LIMIT; i < granted.length; i++) {
            assertThat(granted[i] - granted[i - LIMIT])
                    .as("permits %d and %d", i - LIMIT, i)
                    .isGreaterThanOrEqualTo(SECOND_NANOS);
        }
    }

    @Test
    void agentLlmCallsWaitForPermits() throws Exception {
        int calls = LIMIT * 2 + 1;
        long start = System.nanoTime();
        List<CompletableFuture<AgentAnswer>> answers = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            answers.add(agents.chat("limited-" + i, "hello", Duration.ofSeconds(20))
                    .toCompletableFuture());
        }
        CompletableFuture.allOf(answers.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // 2 * LIMIT + 1 calls need at least three windows, so at least two seconds.
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(2 * SECOND_NANOS);
    }
}
