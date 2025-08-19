package io.github.seonwkim.metrics.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InvokeAllAdviceEventInterceptorsHolderTest {

    @AfterEach
    void tearDown() {
        InvokeAllAdviceEventInterceptorsHolder.reset();
    }

    @Test
    void testOnEnterAndOnExit() {
        // Given
        AtomicBoolean onEnterCalled = new AtomicBoolean(false);
        AtomicBoolean onExitCalled = new AtomicBoolean(false);
        Object testMessages = new Object();
        long testStartTime = System.currentTimeMillis();

        var interceptor = new InvokeAllAdviceEventInterceptorsHolder.InvokeAllAdviceEventInterceptor() {
            @Override
            public void onEnter(Object messages) {
                onEnterCalled.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
                onExitCalled.set(true);
            }
        };

        // When
        InvokeAllAdviceEventInterceptorsHolder.register(interceptor);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

        // Then
        assertTrue(onEnterCalled.get(), "onEnter should have been called");
        assertTrue(onExitCalled.get(), "onExit should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean onEnterCalled = new AtomicBoolean(false);
        AtomicBoolean onExitCalled = new AtomicBoolean(false);
        Object testMessages = new Object();
        long testStartTime = System.currentTimeMillis();

        var interceptor = new InvokeAllAdviceEventInterceptorsHolder.InvokeAllAdviceEventInterceptor() {
            @Override
            public void onEnter(Object messages) {
                onEnterCalled.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
                onExitCalled.set(true);
            }
        };

        // When
        InvokeAllAdviceEventInterceptorsHolder.register(interceptor);
        InvokeAllAdviceEventInterceptorsHolder.unregister(interceptor);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

        // Then
        assertFalse(onEnterCalled.get(), "onEnter should not have been called after unregistering");
        assertFalse(onExitCalled.get(), "onExit should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean onEnterCalled = new AtomicBoolean(false);
        AtomicBoolean onExitCalled = new AtomicBoolean(false);
        Object testMessages = new Object();
        long testStartTime = System.currentTimeMillis();

        var interceptor = new InvokeAllAdviceEventInterceptorsHolder.InvokeAllAdviceEventInterceptor() {
            @Override
            public void onEnter(Object messages) {
                onEnterCalled.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
                onExitCalled.set(true);
            }
        };

        // When
        InvokeAllAdviceEventInterceptorsHolder.register(interceptor);
        InvokeAllAdviceEventInterceptorsHolder.reset();
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

        // Then
        assertFalse(onEnterCalled.get(), "onEnter should not have been called after reset");
        assertFalse(onExitCalled.get(), "onExit should not have been called after reset");
    }

    @Test
    void testMultipleInterceptors() {
        // Given
        AtomicBoolean interceptor1Called = new AtomicBoolean(false);
        AtomicBoolean interceptor2Called = new AtomicBoolean(false);
        Object testMessages = new Object();

        var interceptor1 = new InvokeAllAdviceEventInterceptorsHolder.InvokeAllAdviceEventInterceptor() {
            @Override
            public void onEnter(Object messages) {
                interceptor1Called.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
            }
        };

        var interceptor2 = new InvokeAllAdviceEventInterceptorsHolder.InvokeAllAdviceEventInterceptor() {
            @Override
            public void onEnter(Object messages) {
                interceptor2Called.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
            }
        };

        // When
        InvokeAllAdviceEventInterceptorsHolder.register(interceptor1);
        InvokeAllAdviceEventInterceptorsHolder.register(interceptor2);
        InvokeAllAdviceEventInterceptorsHolder.invokeAllAdviceOnEnter(testMessages);

        // Then
        assertTrue(interceptor1Called.get(), "Interceptor 1 should have been called");
        assertTrue(interceptor2Called.get(), "Interceptor 2 should have been called");
    }
}
