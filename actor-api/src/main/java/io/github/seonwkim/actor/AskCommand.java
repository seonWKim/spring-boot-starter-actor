package io.github.seonwkim.actor;

import javax.annotation.Nullable;

/**
 * Base class for commands that expect a reply from the actor.
 * This class provides a clean and type-safe way to handle ask patterns
 * without requiring explicit lambda functions or type annotations.
 *
 * <p>The framework automatically manages the reply-to reference, so implementing
 * classes don't need to handle it manually. {@code ask} sends a shallow copy of the command that
 * carries the reply address and leaves the command you pass unchanged, so one instance can be
 * asked again or shared between threads.
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
 * CompletionStage<String> result = springActorHandle.ask(new GetUserName("user123"));
 * }
 * </pre>
 *
 * @param <RES> The type of the expected response
 */
public abstract class AskCommand<RES> implements Cloneable {

    // Serialized by the starter's Jackson module for SpringActorRef, so a command can cross nodes.
    @Nullable private SpringActorRef<RES> replyTo;

    // Set on the copy an ask sends: whether a handler failure fails the ask at once.
    private boolean failFast;

    /**
     * Gets the reference that should receive the reply.
     * This method is managed by the framework and should not be called by user code.
     *
     * @return The reference for the reply
     */
    @Nullable public final SpringActorRef<RES> getReplyTo() {
        return replyTo;
    }

    /**
     * Sets the reply-to reference for this command.
     *
     * @param replyTo The reference that should receive the reply
     * @return This command instance
     * @deprecated The framework no longer calls it: {@code ask} sends a copy made with
     *     {@link #copyForAsk(SpringActorRef, boolean)} and leaves this command unchanged. Setting the
     *     reply address on a shared instance makes it unsafe to send twice.
     */
    @Deprecated(since = "1.1.0")
    public final AskCommand<RES> withReplyTo(SpringActorRef<RES> replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    /**
     * A shallow copy of this command that replies to {@code replyTo}; this command is unchanged.
     * Called by the framework for each ask, not by user code.
     *
     * @param replyTo The reference that should receive the reply
     * @param failFast Whether a handler failure on this command fails the ask at once
     * @return The copy to send
     */
    @SuppressWarnings("unchecked")
    public final AskCommand<RES> copyForAsk(SpringActorRef<RES> replyTo, boolean failFast) {
        AskCommand<RES> copy;
        try {
            copy = (AskCommand<RES>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("AskCommand is Cloneable", e);
        }
        copy.replyTo = replyTo;
        copy.failFast = failFast;
        return copy;
    }

    /**
     * Whether the ask that sent this command asked to fail at once when a handler throws on it.
     * Read by the framework, not by user code.
     *
     * @return true for a command sent by an ask with {@code failFastOnError()}
     */
    public final boolean failsFast() {
        return failFast;
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
                    + "Ensure you're using SpringActorHandle.ask() or a similar ask pattern, "
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
