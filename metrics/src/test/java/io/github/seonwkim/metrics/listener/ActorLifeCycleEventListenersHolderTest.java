package io.github.seonwkim.metrics.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ActorLifeCycleEventListenersHolderTest {

    @Test
    void testOnActorCreated() {
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var listener = new ActorLifeCycleEventListenersHolder.ActorLifecycleEventListener() {
            @Override
            public void onActorCreated(Object actorCell) {
                listenerCalled.set(true);
            }

            @Override
            public void onActorTerminated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
                // Not testing this method here
            }
        };

        // When
        ActorLifeCycleEventListenersHolder.register(listener);
        ActorLifeCycleEventListenersHolder.onActorCreated(testActorCell);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }

    @Test
    void testOnActorTerminated() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var listener = new ActorLifeCycleEventListenersHolder.ActorLifecycleEventListener() {
            @Override
            public void onActorCreated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onActorTerminated(Object actorCell) {
                listenerCalled.set(true);
            }

            @Override
            public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
                // Not testing this method here
            }
        };

        // When
        ActorLifeCycleEventListenersHolder.register(listener);
        ActorLifeCycleEventListenersHolder.onActorTerminated(testActorCell);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }

    @Test
    void testOnUnstartedCellReplaced() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testUnstartedCell = new Object();
        Object testNewCell = new Object();

        var listener = new ActorLifeCycleEventListenersHolder.ActorLifecycleEventListener() {
            @Override
            public void onActorCreated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onActorTerminated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
                listenerCalled.set(true);
            }
        };

        // When
        ActorLifeCycleEventListenersHolder.register(listener);
        ActorLifeCycleEventListenersHolder.onUnstartedCellReplaced(testUnstartedCell, testNewCell);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }
    
    @Test
    void testReset() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var listener = new ActorLifeCycleEventListenersHolder.ActorLifecycleEventListener() {
            @Override
            public void onActorCreated(Object actorCell) {
                listenerCalled.set(true);
            }

            @Override
            public void onActorTerminated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
                // Not testing this method here
            }
        };

        // When
        ActorLifeCycleEventListenersHolder.register(listener);
        ActorLifeCycleEventListenersHolder.reset();
        ActorLifeCycleEventListenersHolder.onActorCreated(testActorCell);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after reset");
    }
}
