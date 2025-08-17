package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.japi.function.Function;

/**
 * A wrapper around Pekko's EntityRef that provides methods for asking and telling messages to a
 * sharded actor. This class simplifies interaction with sharded actors by providing a more
 * Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringShardedActorRef<T> {

	private final Scheduler scheduler;
	private final EntityRef<T> entityRef;
	private final Duration defaultTimeout;
	
	/**
	 * Creates a builder for SpringShardedActorRef.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param entityRef The entity reference to wrap
	 * @param <T> The type of messages that the actor can handle
	 * @return A new builder for SpringShardedActorRef
	 */
	public static <T> SpringShardedActorRefBuilder<T> builder(Scheduler scheduler, EntityRef<T> entityRef) {
		return new SpringShardedActorRefBuilder<>(scheduler, entityRef);
	}
	
	/**
	 * Default value for the default timeout in seconds.
	 */
	public static final int DEFAULT_TIMEOUT_SECONDS = 3;

	/**
	 * Creates a new SpringShardedActorRef with the given scheduler and entity reference.
	 * Uses the default timeout of 3 seconds.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param entityRef The entity reference to wrap
	 */
	public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef) {
		this(scheduler, entityRef, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
	}
	
	/**
	 * Creates a new SpringShardedActorRef with the given scheduler, entity reference, and default timeout.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param entityRef The entity reference to wrap
	 * @param defaultTimeout The default timeout for ask operations
	 */
	public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef, Duration defaultTimeout) {
		this.scheduler = scheduler;
		this.entityRef = entityRef;
		this.defaultTimeout = defaultTimeout;
	}

	/**
	 * Asks the sharded actor a question and expects a response, using the default timeout. This
	 * method sends a message to the actor and returns a CompletionStage that will be completed with
	 * the response.
	 *
	 * @param messageFactory A function that creates a message given a reply-to actor reference
	 * @param <REQ> The type of the request message
	 * @param <RES> The type of the response message
	 * @return A CompletionStage that will be completed with the response
	 */
	public <REQ extends T, RES> CompletionStage<RES> ask(
			Function<ActorRef<RES>, REQ> messageFactory) {
		return ask(messageFactory, defaultTimeout);
	}

	/**
	 * Asks the sharded actor a question and expects a response. This method sends a message to the
	 * actor and returns a CompletionStage that will be completed with the response.
	 *
	 * @param messageFactory A function that creates a message given a reply-to actor reference
	 * @param timeout The maximum time to wait for a response
	 * @param <REQ> The type of the request message
	 * @param <RES> The type of the response message
	 * @return A CompletionStage that will be completed with the response
	 */
	public <REQ extends T, RES> CompletionStage<RES> ask(
			Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
		return AskPattern.ask(entityRef, messageFactory::apply, timeout, scheduler);
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
