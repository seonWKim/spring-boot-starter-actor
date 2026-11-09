package io.github.seonwkim.example.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code agent.*} in application.yml. */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** LLM calls one node may start per second, across all its agents. */
    private int callsPerSecond = 20;

    /** How long an agent waits for a human to approve a tool call. */
    private Duration approvalTimeout = Duration.ofSeconds(30);

    /** Tool calls allowed in one turn before the agent gives up. */
    private int maxToolSteps = 5;

    /** Where agent snapshots are written. */
    private String snapshotDir = System.getProperty("java.io.tmpdir") + "/agent-snapshots";

    /** Delay of each fake LLM reply, to mimic a slow model. */
    private Duration fakeLlmLatency = Duration.ZERO;

    /** Run one demo conversation on start-up and log it. */
    private boolean demo = true;

    public int getCallsPerSecond() {
        return callsPerSecond;
    }

    public void setCallsPerSecond(int callsPerSecond) {
        this.callsPerSecond = callsPerSecond;
    }

    public Duration getApprovalTimeout() {
        return approvalTimeout;
    }

    public void setApprovalTimeout(Duration approvalTimeout) {
        this.approvalTimeout = approvalTimeout;
    }

    public int getMaxToolSteps() {
        return maxToolSteps;
    }

    public void setMaxToolSteps(int maxToolSteps) {
        this.maxToolSteps = maxToolSteps;
    }

    public String getSnapshotDir() {
        return snapshotDir;
    }

    public void setSnapshotDir(String snapshotDir) {
        this.snapshotDir = snapshotDir;
    }

    public Duration getFakeLlmLatency() {
        return fakeLlmLatency;
    }

    public void setFakeLlmLatency(Duration fakeLlmLatency) {
        this.fakeLlmLatency = fakeLlmLatency;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }
}
