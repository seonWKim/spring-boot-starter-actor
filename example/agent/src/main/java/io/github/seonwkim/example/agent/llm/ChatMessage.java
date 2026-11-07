package io.github.seonwkim.example.agent.llm;

/**
 * One entry of an agent conversation.
 *
 * @param role who wrote the entry
 * @param content the text, the tool argument for a tool call, or the tool output for a tool result
 * @param toolName the tool for {@link Role#TOOL_CALL} and {@link Role#TOOL_RESULT}, else null
 */
public record ChatMessage(Role role, String content, String toolName) {

    public enum Role {
        USER,
        ASSISTANT,
        TOOL_CALL,
        TOOL_RESULT
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, null);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text, null);
    }

    public static ChatMessage toolCall(String toolName, String argument) {
        return new ChatMessage(Role.TOOL_CALL, argument, toolName);
    }

    public static ChatMessage toolResult(String toolName, String output) {
        return new ChatMessage(Role.TOOL_RESULT, output, toolName);
    }
}
