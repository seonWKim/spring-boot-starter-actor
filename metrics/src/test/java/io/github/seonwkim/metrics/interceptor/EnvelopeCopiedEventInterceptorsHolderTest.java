package io.github.seonwkim.metrics.interceptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeCopiedEventInterceptorsHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeCopiedEventInterceptorsHolder.reset();
    }

    @Test
    void testOnEnvelopeCopied() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCopiedEventInterceptorsHolder.EnvelopeCopiedEventInterceptor() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventInterceptorsHolder.register(interceptor);
        EnvelopeCopiedEventInterceptorsHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCopiedEventInterceptorsHolder.EnvelopeCopiedEventInterceptor() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventInterceptorsHolder.register(interceptor);
        EnvelopeCopiedEventInterceptorsHolder.unregister(interceptor);
        EnvelopeCopiedEventInterceptorsHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object oldEnvelope = new Object();
        Object newEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCopiedEventInterceptorsHolder.EnvelopeCopiedEventInterceptor() {
            @Override
            public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCopiedEventInterceptorsHolder.register(interceptor);
        EnvelopeCopiedEventInterceptorsHolder.reset();
        EnvelopeCopiedEventInterceptorsHolder.onEnvelopeCopied(oldEnvelope, newEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after reset");
    }
}
