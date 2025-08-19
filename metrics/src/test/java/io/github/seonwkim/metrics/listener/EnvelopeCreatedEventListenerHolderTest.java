package io.github.seonwkim.metrics.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeCreatedEventListenerHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeCreatedEventListenerHolder.reset();
    }

    @Test
    void testOnEnvelopeCreated() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCreatedEventListenerHolder.EnvelopeCreatedEventListener() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventListenerHolder.register(listener);
        EnvelopeCreatedEventListenerHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCreatedEventListenerHolder.EnvelopeCreatedEventListener() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventListenerHolder.register(listener);
        EnvelopeCreatedEventListenerHolder.unregister(listener);
        EnvelopeCreatedEventListenerHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCreatedEventListenerHolder.EnvelopeCreatedEventListener() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventListenerHolder.register(listener);
        EnvelopeCreatedEventListenerHolder.reset();
        EnvelopeCreatedEventListenerHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after reset");
    }
}
