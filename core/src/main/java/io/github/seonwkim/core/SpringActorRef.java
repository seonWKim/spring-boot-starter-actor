package io.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;

import io.github.seonwkim.core.RootGuardian.StopResult;
import io.github.seonwkim.core.impl.DefaultRootGuardian;

/**
 * A wrapper around Pekko's ActorRef that provides methods for asking and telling messages to an
 * actor. This class simplifies interaction with actors by providing a more Spring-friendly API.
 *
 * @param <T> The type of messages that the actor can handle
 */
public class SpringActorRef<T> {

	private final Scheduler scheduler;
	private final ActorRef<T> actorRef;
	private final Duration defaultTimeout;
	private final ActorSystem<RootGuardian.Command> actorSystem;
	private final Class<? extends SpringActor<?, T>> actorClass;
	private final SpringActorContext actorContext;
	
	/**
	 * Creates a builder for SpringActorRef.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param actorRef The actor reference to wrap
	 * @param <T> The type of messages that the actor can handle
	 * @return A new builder for SpringActorRef
	 */
	public static <T> SpringActorRefBuilder<T> builder(Scheduler scheduler, ActorRef<T> actorRef) {
		return new SpringActorRefBuilder<>(scheduler, actorRef);
	}

	/**
	 * Default value for the default timeout in seconds.
	 */
	public static final int DEFAULT_TIMEOUT_SECONDS = 3;

	/**
	 * Creates a new SpringActorRef with the given scheduler and actor reference.
	 * This constructor is deprecated in favor of the constructor with metadata.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param actorRef The actor reference to wrap
	 * @deprecated Use the constructor with actor metadata instead
	 */
	@Deprecated
	public SpringActorRef(Scheduler scheduler, ActorRef<T> actorRef) {
		this(scheduler, actorRef, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
	}

	/**
	 * Creates a new SpringActorRef with the given scheduler, actor reference, and default timeout.
	 * This constructor is deprecated in favor of the constructor with metadata.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param actorRef The actor reference to wrap
	 * @param defaultTimeout The default timeout for ask operations
	 * @deprecated Use the constructor with actor metadata instead
	 */
	@Deprecated
	public SpringActorRef(Scheduler scheduler, ActorRef<T> actorRef, Duration defaultTimeout) {
		this(scheduler, actorRef, defaultTimeout, null, null, null);
	}

	/**
	 * Creates a new SpringActorRef with full metadata for enhanced operations.
	 *
	 * @param scheduler The scheduler to use for asking messages
	 * @param actorRef The actor reference to wrap
	 * @param defaultTimeout The default timeout for ask operations
	 * @param actorSystem The actor system (can be null for legacy compatibility)
	 * @param actorClass The actor class (can be null for legacy compatibility)
	 * @param actorContext The actor context (can be null for legacy compatibility)
	 */
	public SpringActorRef(
			Scheduler scheduler,
			ActorRef<T> actorRef,
			Duration defaultTimeout,
			ActorSystem<RootGuardian.Command> actorSystem,
			Class<? extends SpringActor<?, T>> actorClass,
			SpringActorContext actorContext) {
		this.scheduler = scheduler;
		this.actorRef = actorRef;
		this.defaultTimeout = defaultTimeout;
		this.actorSystem = actorSystem;
		this.actorClass = actorClass;
		this.actorContext = actorContext;
	}

	/**
	 * Asks the actor a question and expects a response, using the default timeout. This method sends
	 * a message to the actor and returns a CompletionStage that will be completed with the response.
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
	 * Asks the actor a question and expects a response. This method sends a message to the actor and
	 * returns a CompletionStage that will be completed with the response.
	 *
	 * @param messageFactory A function that creates a message given a reply-to actor reference
	 * @param timeout The maximum time to wait for a response
	 * @param <REQ> The type of the request message
	 * @param <RES> The type of the response message
	 * @return A CompletionStage that will be completed with the response
	 */
	public <REQ extends T, RES> CompletionStage<RES> ask(
			Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
		return AskPattern.ask(actorRef, messageFactory::apply, timeout, scheduler);
	}

	/**
	 * Sends a message to the actor without expecting a response.
	 *
	 * @param message The message to send
	 */
	public void tell(T message) {
		actorRef.tell(message);
	}

	/**
	 * Returns the underlying actor reference.
	 *
	 * @return The underlying Pekko ActorRef
	 */
	public ActorRef<T> getUnderlying() {
		return actorRef;
	}

	/**
	 * Stops this actor asynchronously.
	 * 
	 * @return A CompletionStage that completes when the stop command has been processed
	 * @throws IllegalStateException If this SpringActorRef was created without metadata
	 */
	public CompletionStage<StopResult> stop() {
		return stop(Duration.ofSeconds(3));
	}

	/**
	 * Stops this actor asynchronously with a custom timeout.
	 * 
	 * @param timeout The maximum time to wait for the stop operation to complete
	 * @return A CompletionStage that completes when the stop command has been processed
	 * @throws IllegalStateException If this SpringActorRef was created without metadata
	 */
	public CompletionStage<StopResult> stop(Duration timeout) {
		if (actorSystem == null || actorClass == null || actorContext == null) {
			throw new IllegalStateException(
				"Cannot stop actor: SpringActorRef was created without required metadata. " +
				"Use SpringActorSystem.spawn() to create actors with full metadata support.");
		}

		return AskPattern.ask(
			actorSystem,
			(ActorRef<StopResult> replyTo) ->
				new DefaultRootGuardian.StopActor(actorClass, actorContext, replyTo),
			timeout,
			scheduler);
	}
}
