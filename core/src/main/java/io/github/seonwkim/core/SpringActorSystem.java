package io.github.seonwkim.core;

import io.github.seonwkim.core.RootGuardian.StopResult;
import io.github.seonwkim.core.behavior.ClusterEventBehavior;
import io.github.seonwkim.core.impl.DefaultRootGuardian;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.MailboxSelector;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;

/**
 * A wrapper around Pekko's ActorSystem that provides methods for spawning actors and getting entity
 * references. This class simplifies interaction with the actor system by providing a more
 * Spring-friendly API. It supports both local and cluster modes.
 */
public class SpringActorSystem implements DisposableBean {

	private final ActorSystem<RootGuardian.Command> actorSystem;
	@Nullable private final Cluster cluster;
	@Nullable private final ClusterSharding clusterSharding;

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3); // configurable if needed

	/**
	 * Creates a new SpringActorSystem in local mode.
	 *
	 * @param actorSystem The underlying Pekko ActorSystem
	 */
	public SpringActorSystem(ActorSystem<RootGuardian.Command> actorSystem) {
		this.actorSystem = actorSystem;
		this.cluster = null;
		this.clusterSharding = null;
	}

	/**
	 * Creates a new SpringActorSystem in cluster mode. This constructor also sets up a listener for
	 * cluster events and publishes them as Spring application events.
	 *
	 * @param actorSystem The underlying Pekko ActorSystem
	 * @param cluster The Pekko Cluster
	 * @param clusterSharding The Pekko ClusterSharding
	 * @param publisher The Spring ApplicationEventPublisher for publishing cluster events
	 */
	public SpringActorSystem(
			ActorSystem<RootGuardian.Command> actorSystem,
			Cluster cluster,
			ClusterSharding clusterSharding,
			ApplicationEventPublisher publisher) {
		this.actorSystem = actorSystem;
		this.cluster = cluster;
		this.clusterSharding = clusterSharding;

		ActorRef<ClusterEvent.ClusterDomainEvent> listener =
				actorSystem.systemActorOf(
						ClusterEventBehavior.create(publisher), "cluster-event-listener", Props.empty());
		cluster.subscriptions().tell(new Subscribe<>(listener, ClusterEvent.ClusterDomainEvent.class));
	}

	/**
	 * Returns the underlying Pekko ActorSystem.
	 *
	 * @return The raw Pekko ActorSystem
	 */
	public ActorSystem<RootGuardian.Command> getRaw() {
		return actorSystem;
	}

	/**
	 * Returns the Pekko Cluster if this SpringActorSystem is in cluster mode.
	 *
	 * @return The Pekko Cluster, or null if this SpringActorSystem is in local mode
	 */
	@Nullable
	public Cluster getCluster() {
		return cluster;
	}

	/**
	 * Spawns a new actor with the given command class and actor ID, using the default timeout. This
	 * method asks the root guardian to create a new actor and returns a CompletionStage that will be
	 * completed with a SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorId The ID of the actor
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(Class<T> commandClass, String actorId) {
		return spawn(commandClass, new DefaultSpringActorContext(actorId));
	}

	/**
	 * Spawns a new actor with the given command class and actor ID, using the default timeout. This
	 * method asks the root guardian to create a new actor and returns a CompletionStage that will be
	 * completed with a SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorContext The context of the actor
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(Class<T> commandClass, SpringActorContext actorContext) {
		return AskPattern.ask(
								 actorSystem,
								 (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
										 new DefaultRootGuardian.SpawnActor<>(
												 commandClass, actorContext, replyTo, MailboxSelector.defaultMailbox(), false),
								 DEFAULT_TIMEOUT,
								 actorSystem.scheduler())
						 .thenApply(spawned -> new SpringActorRef<>(actorSystem.scheduler(), spawned.ref));
	}

	/**
	 * Spawns a new actor with the given command class, actor ID, and timeout. This method asks the
	 * root guardian to create a new actor and returns a CompletionStage that will be completed with a
	 * SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorId The ID of the actor
	 * @param timeout The maximum time to wait for the actor to be created
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(
			Class<T> commandClass, String actorId, Duration timeout) {
		return spawn(commandClass, new DefaultSpringActorContext(actorId), timeout);
	}

	/**
	 * Spawns a new actor with the given command class, actor ID, and timeout. This method asks the
	 * root guardian to create a new actor and returns a CompletionStage that will be completed with a
	 * SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorContext The ID of the actor
	 * @param timeout The maximum time to wait for the actor to be created
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(
			Class<T> commandClass, SpringActorContext actorContext, Duration timeout) {
		return AskPattern.ask(
								 actorSystem,
								 (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
										 new DefaultRootGuardian.SpawnActor<>(
												 commandClass, actorContext, replyTo, MailboxSelector.defaultMailbox(), false),
								 timeout,
								 actorSystem.scheduler())
						 .thenApply(spawned -> new SpringActorRef<>(actorSystem.scheduler(), spawned.ref));
	}

	/**
	 * Spawns a new actor with the given command class, actor ID, and timeout. This method asks the
	 * root guardian to create a new actor and returns a CompletionStage that will be completed with a
	 * SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorId The ID of the actor
	 * @param timeout The maximum time to wait for the actor to be created
	 * @param mailboxSelector The mailbox configuration to use for the actor, such as a bounded or
	 *     unbounded mailbox, which can affect throughput and message prioritization behavior.
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(
			Class<T> commandClass, String actorId, Duration timeout, MailboxSelector mailboxSelector) {
		return spawn(commandClass, new DefaultSpringActorContext(actorId), timeout, mailboxSelector);
	}

	/**
	 * Spawns a new actor with the given command class, actor ID, and timeout. This method asks the
	 * root guardian to create a new actor and returns a CompletionStage that will be completed with a
	 * SpringActorRef to the new actor.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorContext The ID of the actor
	 * @param timeout The maximum time to wait for the actor to be created
	 * @param mailboxSelector The mailbox configuration to use for the actor, such as a bounded or
	 *     unbounded mailbox, which can affect throughput and message prioritization behavior.
	 * @param <T> The type of commands that the actor can handle
	 * @return A CompletionStage that will be completed with a SpringActorRef to the new actor
	 */
	public <T> CompletionStage<SpringActorRef<T>> spawn(
			Class<T> commandClass, SpringActorContext actorContext, Duration timeout, MailboxSelector mailboxSelector) {
		return AskPattern.ask(
								 actorSystem,
								 (ActorRef<DefaultRootGuardian.Spawned<T>> replyTo) ->
										 new DefaultRootGuardian.SpawnActor<>(
												 commandClass, actorContext, replyTo, mailboxSelector, false),
								 timeout,
								 actorSystem.scheduler())
						 .thenApply(spawned -> new SpringActorRef<>(actorSystem.scheduler(), spawned.ref));
	}

	/**
	 * Asynchronously stops a previously spawned actor identified by its command class and actor ID.
	 *
	 * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
	 * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
	 * will still complete successfully with a {@link StopResult} response indicating the request was
	 * acknowledged.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorId The ID of the actor to stop
	 * @param <T> The type of commands that the actor can handle
	 * @return A {@link CompletionStage} that completes when the stop command has been processed
	 */
	public <T> CompletionStage<StopResult> stop(Class<T> commandClass, String actorId) {
		return stop(commandClass, new DefaultSpringActorContext(actorId));
	}

	/**
	 * Asynchronously stops a previously spawned actor identified by its command class and actor ID.
	 *
	 * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
	 * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
	 * will still complete successfully with a {@link StopResult} response indicating the request was
	 * acknowledged.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorContext The context of the actor to stop
	 * @param <T> The type of commands that the actor can handle
	 * @return A {@link CompletionStage} that completes when the stop command has been processed
	 */
	public <T> CompletionStage<StopResult> stop(Class<T> commandClass, SpringActorContext actorContext) {
		return AskPattern.ask(
				actorSystem,
				(ActorRef<DefaultRootGuardian.StopResult> replyTo) ->
						new DefaultRootGuardian.StopActor<>(commandClass, actorContext, replyTo),
				DEFAULT_TIMEOUT,
				actorSystem.scheduler());
	}

	/**
	 * Asynchronously stops a previously spawned actor identified by its command class and actor ID,
	 * with a custom timeout. This method sends a {@link DefaultRootGuardian.StopActor} command to the
	 * root guardian, which is responsible for managing the lifecycle of actors within the system.
	 *
	 * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
	 * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
	 * will still complete successfully with a {@link StopResult} response indicating the request was
	 * acknowledged.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorId The ID of the actor to stop
	 * @param timeout The maximum time to wait for the stop operation to complete
	 * @param <T> The type of commands that the actor can handle
	 * @return A {@link CompletionStage} that completes when the stop command has been processed
	 */
	public <T> CompletionStage<StopResult> stop(
			Class<T> commandClass, String actorId, Duration timeout) {
		return stop(commandClass, new DefaultSpringActorContext(actorId), timeout);
	}

	/**
	 * Asynchronously stops a previously spawned actor identified by its command class and actor ID,
	 * with a custom timeout. This method sends a {@link DefaultRootGuardian.StopActor} command to the
	 * root guardian, which is responsible for managing the lifecycle of actors within the system.
	 *
	 * <p>If the actor exists and is currently active, it will be gracefully stopped. If the actor
	 * does not exist or has already been passivated or stopped, the returned {@link CompletionStage}
	 * will still complete successfully with a {@link StopResult} response indicating the request was
	 * acknowledged.
	 *
	 * @param commandClass The class of commands that the actor can handle
	 * @param actorContext The actorContext of the actor to stop
	 * @param timeout The maximum time to wait for the stop operation to complete
	 * @param <T> The type of commands that the actor can handle
	 * @return A {@link CompletionStage} that completes when the stop command has been processed
	 */
	public <T> CompletionStage<StopResult> stop(
			Class<T> commandClass, SpringActorContext actorContext, Duration timeout) {
		return AskPattern.ask(
				actorSystem,
				(ActorRef<DefaultRootGuardian.StopResult> replyTo) ->
						new DefaultRootGuardian.StopActor<>(commandClass, actorContext, replyTo),
				timeout,
				actorSystem.scheduler());
	}

	/**
	 * Returns a reference to a sharded entity with the given entity type key and entity ID. This
	 * method can only be called if this SpringActorSystem is in cluster mode.
	 *
	 * @param entityTypeKey The entity type key
	 * @param entityId The entity ID
	 * @param <T> The type of messages that the entity can handle
	 * @return A SpringShardedActorRef to the entity
	 * @throws IllegalStateException If this SpringActorSystem is not in cluster mode
	 */
	public <T> SpringShardedActorRef<T> entityRef(EntityTypeKey<T> entityTypeKey, String entityId) {
		if (clusterSharding == null) {
			throw new IllegalStateException("Cluster sharding not configured");
		}

		final EntityRef<T> entityRef = clusterSharding.entityRefFor(entityTypeKey, entityId);
		return new SpringShardedActorRef<>(actorSystem.scheduler(), entityRef);
	}

	/**
	 * Terminates the actor system and waits for it to terminate. This method is called by Spring when
	 * the application context is closed.
	 */
	@Override
	public void destroy() {
		actorSystem.terminate();
		actorSystem.getWhenTerminated().toCompletableFuture().join();
	}
}
