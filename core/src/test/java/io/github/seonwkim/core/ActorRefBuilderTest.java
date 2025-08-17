package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Tests for the builder pattern implementation of SpringActorRef and SpringShardedActorRef.
 */
public class ActorRefBuilderTest {

    @Test
    public void testSpringActorRefBuilder() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        ActorRef<String> actorRef = (ActorRef<String>) mock(ActorRef.class);
        Duration customTimeout = Duration.ofSeconds(10);

        // Act
        SpringActorRef<String> ref = SpringActorRef.builder(scheduler, actorRef)
                .withTimeout(customTimeout)
                .build();

        // Assert
        assertNotNull(ref);
        assertEquals(actorRef, ref.getUnderlying());
    }

    @Test
    public void testSpringActorRefBuilderWithTimeoutSeconds() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        ActorRef<String> actorRef = (ActorRef<String>) mock(ActorRef.class);
        int timeoutSeconds = 5;

        // Act
        SpringActorRef<String> ref = SpringActorRef.builder(scheduler, actorRef)
                .withTimeoutSeconds(timeoutSeconds)
                .build();

        // Assert
        assertNotNull(ref);
        assertEquals(actorRef, ref.getUnderlying());
    }

    @Test
    public void testSpringShardedActorRefBuilder() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        EntityRef<String> entityRef = (EntityRef<String>) mock(EntityRef.class);
        Duration customTimeout = Duration.ofSeconds(10);

        // Act
        SpringShardedActorRef<String> ref = SpringShardedActorRef.builder(scheduler, entityRef)
                .withTimeout(customTimeout)
                .build();

        // Assert
        assertNotNull(ref);
    }

    @Test
    public void testSpringShardedActorRefBuilderWithTimeoutSeconds() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        EntityRef<String> entityRef = (EntityRef<String>) mock(EntityRef.class);
        int timeoutSeconds = 5;

        // Act
        SpringShardedActorRef<String> ref = SpringShardedActorRef.builder(scheduler, entityRef)
                .withTimeoutSeconds(timeoutSeconds)
                .build();

        // Assert
        assertNotNull(ref);
    }
}
