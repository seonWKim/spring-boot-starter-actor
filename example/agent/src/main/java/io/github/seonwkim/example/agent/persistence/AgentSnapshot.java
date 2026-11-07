package io.github.seonwkim.example.agent.persistence;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import java.util.List;

/** What an agent needs to resume: its whole conversation. */
public record AgentSnapshot(String agentId, List<ChatMessage> conversation) {}
