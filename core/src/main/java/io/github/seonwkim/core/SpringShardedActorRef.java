package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.japi.function.Function;

/**
 * A wrapper around Pekko's EntityRef that provides methods for asking and telling messages to a sharded actor.
 * This class simplifies interaction with sharded actors by providing a more Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringShardedActorRef<T> {

    private final Scheduler scheduler;
    private final EntityRef<T> entityRef;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Creates a new SpringShardedActorRef with the given scheduler and entity reference.
     *
     * @param scheduler The scheduler to use for asking messages
     * @param entityRef The entity reference to wrap
     */
    public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef) {
        this.scheduler = scheduler;
        this.entityRef = entityRef;
    }

    /**
     * Asks the sharded actor a question and expects a response.
     * This method sends a message to the actor and returns a CompletionStage that will be completed with the response.
     *
     * @param messageFactory A function that creates a message given a reply-to actor reference
     * @param timeout The maximum time to wait for a response
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     * @return A CompletionStage that will be completed with the response
     */
    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
        @SuppressWarnings("unchecked")
        RecipientRef<REQ> recipient = (RecipientRef<REQ>) entityRef;
        return AskPattern.ask(recipient, messageFactory, timeout, scheduler);
    }

    /**
     * Asks the sharded actor a question and expects a response, using the default timeout.
     * This method sends a message to the actor and returns a CompletionStage that will be completed with the response.
     *
     * @param messageFactory A function that creates a message given a reply-to actor reference
     * @param <REQ> The type of the request message
     * @param <RES> The type of the response message
     * @return A CompletionStage that will be completed with the response
     */
    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory) {
        return ask(messageFactory, DEFAULT_TIMEOUT);
    }

    /**
     * Sends a message to the sharded actor without expecting a response.
     *
     * @param message The message to send
     */
    public void tell(T message) {
        entityRef.tell(message);
    }
}
