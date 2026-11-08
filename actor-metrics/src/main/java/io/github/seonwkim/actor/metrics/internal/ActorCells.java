package io.github.seonwkim.actor.metrics.internal;

import io.github.seonwkim.actor.metrics.api.ActorContext;
import org.apache.pekko.actor.ActorCell;

/** Reads an {@link ActorContext} from a Pekko {@code ActorCell}, the object the advice classes see. */
public final class ActorCells {

    private ActorCells() {}

    public static ActorContext contextOf(Object actorCell) {
        try {
            ActorCell cell = (ActorCell) actorCell;
            // From props, not actor(): the actor instance doesn't exist until the cell has processed
            // Create, and messages sent before that must count under the same class as after it.
            return new ActorContext(
                    cell.self().path().toString(), cell.props().actorClass().getSimpleName());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed to extract actor context from cell", e);
        }
    }
}
