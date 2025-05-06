package org.github.seonwkim.core.behavior;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

public class ClusterEventBehavior {

    public static Behavior<ClusterDomainEvent> create(ApplicationEventPublisher publisher) {
        return Behaviors.receive(ClusterDomainEvent.class)
                        .onMessage(ClusterDomainEvent.class, event -> {
                            publisher.publishEvent(new ClusterDomainWrappedEvent(event));
                            return Behaviors.same();
                        })
                        .build();
    }

    public static class ClusterDomainWrappedEvent extends ApplicationEvent {
        public ClusterDomainWrappedEvent(ClusterDomainEvent event) {
            super(event);
        }

        public ClusterDomainEvent getEvent() {
            return (ClusterDomainEvent) getSource();
        }
    }
}
