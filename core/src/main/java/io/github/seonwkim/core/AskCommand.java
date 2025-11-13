package io.github.seonwkim.core;

import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;

/**
 * Base class for commands that expect a reply from the actor.
 * This class provides a clean and type-safe way to handle ask patterns
 * without requiring explicit lambda functions or type annotations.
 *
 * <p>The framework automatically manages the reply-to reference, so implementing
 * classes don't need to handle it manually.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * public class GetUserName extends AskCommand<String> {
 *     private final String userId;
 *
 *     public GetUserName(String userId) {
 *         this.userId = userId;
 *     }
 *
 *     public String getUserId() {
 *         return userId;
 *     }
 * }
 *
 * // Usage:
 * CompletionStage<String> result = springActorRef.ask(new GetUserName("user123"));
 * }
 * </pre>
 *
 * @param <RES> The type of the expected response
 */
public abstract class AskCommand<RES> {

    @Nullable private ActorRef<RES> replyTo;

    /**
     * Gets the ActorRef that should receive the reply.
     * This method is managed by the framework and should not be called by user code.
     *
     * @return The ActorRef for the reply
     */
    @Nullable public final ActorRef<RES> getReplyTo() {
        return replyTo;
    }

    /**
     * Sets the reply-to reference for this command.
     * This method is called by the framework and should not be called by user code.
     *
     * @param replyTo The ActorRef that should receive the reply
     * @return This command instance
     */
    public final AskCommand<RES> withReplyTo(ActorRef<RES> replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    /**
     * Sends a response back to the actor that sent this command.
     * This is a convenience method that handles null checking internally.
     *
     * <p>Example usage:
     * <pre>
     * {@code
     * private Behavior<Command> onGetUserName(GetUserName msg) {
     *     String name = userRepository.findById(msg.getUserId()).getName();
     *     msg.reply(name);  // Safe - handles null checking
     *     return Behaviors.same();
     * }
     * }
     * </pre>
     *
     * @param response The response to send back
     * @throws IllegalStateException if replyTo has not been set by the framework
     */
    public final void reply(RES response) {
        if (replyTo == null) {
            throw new IllegalStateException("Cannot send reply: replyTo has not been set. "
                    + "This command was not sent through the ask() method. "
                    + "Ensure you're using SpringActorRef.ask() or a similar ask pattern, "
                    + "not tell() which doesn't expect a response. "
                    + "Command type: "
                    + this.getClass().getName());
        }
        replyTo.tell(response);
    }

    /**
     * Checks if this command has a reply-to reference set.
     * This can be useful for commands that can be used in both ask and tell patterns.
     *
     * @return true if replyTo is set, false otherwise
     */
    public final boolean hasReplyTo() {
        return replyTo != null;
    }
}
