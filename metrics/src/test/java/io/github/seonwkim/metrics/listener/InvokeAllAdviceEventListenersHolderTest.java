package io.github.seonwkim.metrics.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InvokeAllAdviceEventListenersHolderTest {

    @AfterEach
    void tearDown() {
        InvokeAllAdviceEventListenersHolder.reset();
    }

    @Test
    void testOnEnterAndOnExit() {
        // Given
        AtomicBoolean onEnterCalled = new AtomicBoolean(false);
        AtomicBoolean onExitCalled = new AtomicBoolean(false);
        Object testMessages = new Object();
        long testStartTime = System.currentTimeMillis();

        var listener = new InvokeAllAdviceEventListenersHolder.InvokeAllAdviceEventListener() {
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
        InvokeAllAdviceEventListenersHolder.register(listener);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

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

        var listener = new InvokeAllAdviceEventListenersHolder.InvokeAllAdviceEventListener() {
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
        InvokeAllAdviceEventListenersHolder.register(listener);
        InvokeAllAdviceEventListenersHolder.unregister(listener);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

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

        var listener = new InvokeAllAdviceEventListenersHolder.InvokeAllAdviceEventListener() {
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
        InvokeAllAdviceEventListenersHolder.register(listener);
        InvokeAllAdviceEventListenersHolder.reset();
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnEnter(testMessages);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnExit(testMessages, testStartTime);

        // Then
        assertFalse(onEnterCalled.get(), "onEnter should not have been called after reset");
        assertFalse(onExitCalled.get(), "onExit should not have been called after reset");
    }

    @Test
    void testMultipleListeners() {
        // Given
        AtomicBoolean listener1Called = new AtomicBoolean(false);
        AtomicBoolean listener2Called = new AtomicBoolean(false);
        Object testMessages = new Object();

        var listener1 = new InvokeAllAdviceEventListenersHolder.InvokeAllAdviceEventListener() {
            @Override
            public void onEnter(Object messages) {
                listener1Called.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
            }
        };

        var listener2 = new InvokeAllAdviceEventListenersHolder.InvokeAllAdviceEventListener() {
            @Override
            public void onEnter(Object messages) {
                listener2Called.set(true);
            }

            @Override
            public void onExit(Object messages, long startTime) {
            }
        };

        // When
        InvokeAllAdviceEventListenersHolder.register(listener1);
        InvokeAllAdviceEventListenersHolder.register(listener2);
        InvokeAllAdviceEventListenersHolder.invokeAllAdviceOnEnter(testMessages);

        // Then
        assertTrue(listener1Called.get(), "Listener 1 should have been called");
        assertTrue(listener2Called.get(), "Listener 2 should have been called");
    }
}