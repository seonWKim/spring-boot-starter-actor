package io.github.seonwkim.example.agent.tool;

// --8<-- [start:tool]
/** A function the model may ask the agent to run. */
public interface Tool {

    String name();

    /** Tools that change the world wait for a human to approve the call. */
    default boolean requiresApproval() {
        return false;
    }

    String invoke(String argument);
}
// --8<-- [end:tool]
