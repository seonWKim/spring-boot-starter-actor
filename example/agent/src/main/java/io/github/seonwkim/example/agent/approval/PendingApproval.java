package io.github.seonwkim.example.agent.approval;

/** A tool call waiting for a human. */
public record PendingApproval(String requestId, String agentId, String toolName, String argument) {}
