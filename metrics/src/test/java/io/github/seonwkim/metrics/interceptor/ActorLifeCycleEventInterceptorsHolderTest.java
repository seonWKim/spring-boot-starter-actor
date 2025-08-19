package io.github.seonwkim.metrics.interceptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ActorLifeCycleEventInterceptorsHolderTest {

    @Test
    void testOnActorCreated() {
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var interceptor = new ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor() {
            @Override
            public void onActorCreated(Object actorCell) {
                interceptorCalled.set(true);
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
        ActorLifeCycleEventInterceptorsHolder.register(interceptor);
        ActorLifeCycleEventInterceptorsHolder.onActorCreated(testActorCell);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }

    @Test
    void testOnActorTerminated() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var interceptor = new ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor() {
            @Override
            public void onActorCreated(Object actorCell) {
                // Not testing this method here
            }

            @Override
            public void onActorTerminated(Object actorCell) {
                interceptorCalled.set(true);
            }

            @Override
            public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
                // Not testing this method here
            }
        };

        // When
        ActorLifeCycleEventInterceptorsHolder.register(interceptor);
        ActorLifeCycleEventInterceptorsHolder.onActorTerminated(testActorCell);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }

    @Test
    void testOnUnstartedCellReplaced() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testUnstartedCell = new Object();
        Object testNewCell = new Object();

        var interceptor = new ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor() {
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
                interceptorCalled.set(true);
            }
        };

        // When
        ActorLifeCycleEventInterceptorsHolder.register(interceptor);
        ActorLifeCycleEventInterceptorsHolder.onUnstartedCellReplaced(testUnstartedCell, testNewCell);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }
    
    @Test
    void testReset() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testActorCell = new Object();

        var interceptor = new ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor() {
            @Override
            public void onActorCreated(Object actorCell) {
                interceptorCalled.set(true);
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
        ActorLifeCycleEventInterceptorsHolder.register(interceptor);
        ActorLifeCycleEventInterceptorsHolder.reset();
        ActorLifeCycleEventInterceptorsHolder.onActorCreated(testActorCell);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after reset");
    }
}
