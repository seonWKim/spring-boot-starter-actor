package io.github.seonwkim.metrics.interceptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeCreatedEventInterceptorsHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeCreatedEventInterceptorsHolder.reset();
    }

    @Test
    void testOnEnvelopeCreated() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCreatedEventInterceptorsHolder.EnvelopeCreatedEventInterceptor() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventInterceptorsHolder.register(interceptor);
        EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCreatedEventInterceptorsHolder.EnvelopeCreatedEventInterceptor() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventInterceptorsHolder.register(interceptor);
        EnvelopeCreatedEventInterceptorsHolder.unregister(interceptor);
        EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeCreatedEventInterceptorsHolder.EnvelopeCreatedEventInterceptor() {
            @Override
            public void onEnvelopeCreated(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeCreatedEventInterceptorsHolder.register(interceptor);
        EnvelopeCreatedEventInterceptorsHolder.reset();
        EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(testEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after reset");
    }
}
