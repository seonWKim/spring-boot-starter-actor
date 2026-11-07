package io.github.seonwkim.example.agent.llm;

/**
 * What the model answered: either a final text or a request to call a tool.
 *
 * @param text the final answer, or null for a tool call
 * @param toolName the tool to call, or null for a final answer
 * @param toolArgument the argument for the tool, or null for a final answer
 */
public record LlmReply(String text, String toolName, String toolArgument) {

    public static LlmReply answer(String text) {
        return new LlmReply(text, null, null);
    }

    public static LlmReply callTool(String toolName, String argument) {
        return new LlmReply(null, toolName, argument);
    }

    public boolean isToolCall() {
        return toolName != null;
    }
}
