package io.github.seonwkim.example.agent;

import io.github.seonwkim.example.agent.llm.ChatMessage;
import java.util.List;

/**
 * A read of an agent's state.
 *
 * @param incarnation a new id each time the agent's actor starts or restarts
 * @param status what the agent waits for: IDLE, PERMIT, LLM or APPROVAL
 * @param queued user messages waiting for the current turn to finish
 */
public record ConversationView(
        String agentId, String incarnation, String status, int queued, List<ChatMessage> conversation) {}
