package io.github.seonwkim.core.behavior;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * A behavior for handling cluster events. This class provides a behavior that listens for cluster
 * events and publishes them as Spring application events.
 */
public class ClusterEventBehavior {

	/**
	 * Creates a behavior that listens for cluster events and publishes them as Spring application
	 * events.
	 *
	 * @param publisher The Spring application event publisher
	 * @return A behavior that handles cluster events
	 */
	public static Behavior<ClusterDomainEvent> create(ApplicationEventPublisher publisher) {
		return Behaviors.receive(ClusterDomainEvent.class)
				.onMessage(
						ClusterDomainEvent.class,
						event -> {
							publisher.publishEvent(new ClusterDomainWrappedEvent(event));
							return Behaviors.same();
						})
				.build();
	}

	/**
	 * A Spring application event that wraps a Pekko cluster domain event. This class allows Pekko
	 * cluster events to be published and consumed using Spring's event system.
	 */
	public static class ClusterDomainWrappedEvent extends ApplicationEvent {
		/**
		 * Creates a new ClusterDomainWrappedEvent with the given cluster domain event.
		 *
		 * @param event The Pekko cluster domain event to wrap
		 */
		public ClusterDomainWrappedEvent(ClusterDomainEvent event) {
			super(event);
		}

		/**
		 * Returns the wrapped Pekko cluster domain event.
		 *
		 * @return The Pekko cluster domain event
		 */
		public ClusterDomainEvent getEvent() {
			return (ClusterDomainEvent) getSource();
		}
	}
}
