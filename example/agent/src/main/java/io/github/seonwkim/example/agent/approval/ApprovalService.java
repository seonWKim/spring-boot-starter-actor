package io.github.seonwkim.example.agent.approval;

import io.github.seonwkim.example.agent.AgentRuntime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;

// --8<-- [start:approval-service]
/** The human side of the approval step, for a controller, a Slack bot or a test. */
@Service
public class ApprovalService {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final AgentRuntime runtime;

    public ApprovalService(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    public CompletionStage<List<PendingApproval>> pending() {
        return runtime.approvals()
                .ask(new ApprovalActor.ListPending())
                .withTimeout(TIMEOUT)
                .execute();
    }

    /** Completes with false if the request is unknown or its timeout has passed. */
    public CompletionStage<Boolean> decide(String requestId, boolean approved) {
        return runtime.approvals()
                .ask(new ApprovalActor.Decide(requestId, approved))
                .withTimeout(TIMEOUT)
                .execute();
    }
}
// --8<-- [end:approval-service]
