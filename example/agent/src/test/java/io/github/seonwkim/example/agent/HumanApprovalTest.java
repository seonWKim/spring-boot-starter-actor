package io.github.seonwkim.example.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.example.agent.approval.ApprovalService;
import io.github.seonwkim.example.agent.approval.PendingApproval;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = "agent.approval-timeout=3s")
@DirtiesContext
class HumanApprovalTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private AgentService agents;

    @Autowired
    private ApprovalService approvals;

    @Test
    void approvedToolCallRuns() throws Exception {
        CompletableFuture<AgentAnswer> answer =
                agents.chat("approve-agent", "refund order-42", TIMEOUT).toCompletableFuture();

        PendingApproval request = awaitPendingFor("approve-agent");
        assertThat(request.toolName()).isEqualTo("refund");
        assertThat(request.argument()).isEqualTo("order-42");
        assertThat(answer).isNotDone();

        assertThat(approvals
                        .decide(request.requestId(), true)
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS))
                .isTrue();
        assertThat(answer.get(10, TimeUnit.SECONDS).text())
                .isEqualTo("Tool refund says: refund issued for order order-42");
    }

    @Test
    void rejectedToolCallDoesNotRun() throws Exception {
        CompletableFuture<AgentAnswer> answer =
                agents.chat("reject-agent", "refund order-43", TIMEOUT).toCompletableFuture();

        PendingApproval request = awaitPendingFor("reject-agent");
        approvals.decide(request.requestId(), false).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(answer.get(10, TimeUnit.SECONDS).text()).isEqualTo("Tool refund says: not run: rejected by a human");
    }

    @Test
    void unansweredApprovalTimesOut() throws Exception {
        long start = System.nanoTime();
        CompletableFuture<AgentAnswer> answer =
                agents.chat("timeout-agent", "refund order-44", TIMEOUT).toCompletableFuture();
        PendingApproval request = awaitPendingFor("timeout-agent");

        assertThat(answer.get(15, TimeUnit.SECONDS).text())
                .isEqualTo("Tool refund says: not run: no human answer within PT3S");
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(3));

        // A late answer is refused: the request expired with the agent's ask.
        await().atMost(5, TimeUnit.SECONDS).until(() -> !approvals
                .decide(request.requestId(), true)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS));
    }

    private PendingApproval awaitPendingFor(String agentId) {
        return await().atMost(10, TimeUnit.SECONDS)
                .until(
                        () -> approvals.pending().toCompletableFuture().get(5, TimeUnit.SECONDS).stream()
                                .filter(request -> request.agentId().equals(agentId))
                                .findFirst()
                                .orElse(null),
                        request -> request != null);
    }
}
