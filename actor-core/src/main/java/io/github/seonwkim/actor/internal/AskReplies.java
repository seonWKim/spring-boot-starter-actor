package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorRef;
import org.apache.pekko.actor.Status;

/** Replies the framework sends to an ask on the handler's behalf. */
public final class AskReplies {

    private AskReplies() {}

    /**
     * Fails the ask that sent {@code message} with {@code error}, if it is a command of an ask with
     * {@code failFastOnError()}. Pekko's ask completes exceptionally on a {@link Status.Failure}.
     */
    public static void failFast(Object message, Throwable error) {
        if (message instanceof AskCommand<?> command && command.failsFast()) {
            SpringActorRef<?> replyTo = command.getReplyTo();
            if (replyTo != null) {
                PekkoActorRef.toPekko(replyTo).unsafeUpcast().tell(new Status.Failure(error));
            }
        }
    }
}
