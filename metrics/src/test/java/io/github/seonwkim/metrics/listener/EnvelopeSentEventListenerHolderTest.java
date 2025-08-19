package io.github.seonwkim.metrics.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeSentEventListenerHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeSentEventListenerHolder.reset();
    }

    @Test
    void testOnEnvelopeSent() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeSentEventListenerHolder.EnvelopeSentEventListener() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventListenerHolder.register(listener);
        EnvelopeSentEventListenerHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeSentEventListenerHolder.EnvelopeSentEventListener() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventListenerHolder.register(listener);
        EnvelopeSentEventListenerHolder.unregister(listener);
        EnvelopeSentEventListenerHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeSentEventListenerHolder.EnvelopeSentEventListener() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventListenerHolder.register(listener);
        EnvelopeSentEventListenerHolder.reset();
        EnvelopeSentEventListenerHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after reset");
    }
}
