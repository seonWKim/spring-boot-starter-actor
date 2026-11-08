package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import io.github.seonwkim.core.shard.SpringShardedActorHandle;
import java.time.Duration;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.junit.jupiter.api.Test;

/** Tests for the builder pattern implementation of SpringActorHandle and SpringShardedActorHandle. */
public class ActorRefBuilderTest {

    @Test
    public void testSpringActorRefBuilder() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        ActorRef<String> actorRef = (ActorRef<String>) mock(ActorRef.class);
        Duration customTimeout = Duration.ofSeconds(10);

        // Act
        SpringActorHandle<String> ref = SpringActorHandle.builder(scheduler, actorRef)
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
        SpringActorHandle<String> ref = SpringActorHandle.builder(scheduler, actorRef)
                .withTimeoutSeconds(timeoutSeconds)
                .build();

        // Assert
        assertNotNull(ref);
        assertEquals(actorRef, ref.getUnderlying());
    }

    @Test
    public void testSpringShardedActorHandleBuilder() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        EntityRef<String> entityRef = (EntityRef<String>) mock(EntityRef.class);
        Duration customTimeout = Duration.ofSeconds(10);

        // Act
        SpringShardedActorHandle<String> ref = SpringShardedActorHandle.builder(scheduler, entityRef)
                .withTimeout(customTimeout)
                .build();

        // Assert
        assertNotNull(ref);
    }

    @Test
    public void testSpringShardedActorHandleBuilderWithTimeoutSeconds() {
        // Arrange
        Scheduler scheduler = mock(Scheduler.class);
        @SuppressWarnings("unchecked")
        EntityRef<String> entityRef = (EntityRef<String>) mock(EntityRef.class);
        int timeoutSeconds = 5;

        // Act
        SpringShardedActorHandle<String> ref = SpringShardedActorHandle.builder(scheduler, entityRef)
                .withTimeoutSeconds(timeoutSeconds)
                .build();

        // Assert
        assertNotNull(ref);
    }
}
