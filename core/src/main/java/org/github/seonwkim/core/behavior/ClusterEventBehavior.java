package org.github.seonwkim.core.behavior;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.ClusterEvent;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

public class ClusterEventBehavior {

    public static Behavior<ClusterDomainEvent> create(ApplicationEventPublisher publisher) {
        return Behaviors.receive(ClusterDomainEvent.class)
                        .onMessage(ClusterEvent.MemberUp.class, event -> {
                            publisher.publishEvent(new MemberUpEvent(event));
                            return Behaviors.same();
                        })
                        .onMessage(ClusterEvent.MemberRemoved.class, event -> {
                            publisher.publishEvent(new MemberRemovedEvent(event));
                            return Behaviors.same();
                        })
                        .build();
    }

    public static class MemberUpEvent extends ApplicationEvent {
        public MemberUpEvent(ClusterEvent.MemberUp event) {
            super(event);
        }

        public ClusterEvent.MemberUp getEvent() {
            return (ClusterEvent.MemberUp) getSource();
        }
    }

    public static class MemberRemovedEvent extends ApplicationEvent {
        public MemberRemovedEvent(ClusterEvent.MemberRemoved event) {
            super(event);
        }

        public ClusterEvent.MemberRemoved getEvent() {
            return (ClusterEvent.MemberRemoved) getSource();
        }
    }
}
