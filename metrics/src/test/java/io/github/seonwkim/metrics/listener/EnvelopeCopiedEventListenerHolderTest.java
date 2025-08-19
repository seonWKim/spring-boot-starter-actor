package io.github.seonwkim.metrics.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeCopiedEventListenerHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeCopiedEventListenerHolder.reset();
    }

    @Test
    void testOnEnvelopeCopied() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCopiedEventListenerHolder.EnvelopeCopiedEventListener() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventListenerHolder.register(listener);
        EnvelopeCopiedEventListenerHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(listenerCalled.get(), "Listener should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCopiedEventListenerHolder.EnvelopeCopiedEventListener() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventListenerHolder.register(listener);
        EnvelopeCopiedEventListenerHolder.unregister(listener);
        EnvelopeCopiedEventListenerHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean listenerCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var listener = new EnvelopeCopiedEventListenerHolder.EnvelopeCopiedEventListener() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                listenerCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventListenerHolder.register(listener);
        EnvelopeCopiedEventListenerHolder.reset();
        EnvelopeCopiedEventListenerHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(!listenerCalled.get(), "Listener should not have been called after reset");
    }
}
