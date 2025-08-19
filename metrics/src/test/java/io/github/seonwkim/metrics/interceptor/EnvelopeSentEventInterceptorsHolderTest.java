package io.github.seonwkim.metrics.interceptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvelopeSentEventInterceptorsHolderTest {

    @AfterEach
    void tearDown() {
        EnvelopeSentEventInterceptorsHolder.reset();
    }

    @Test
    void testOnEnvelopeSent() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeSentEventInterceptorsHolder.EnvelopeSentEventInterceptor() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventInterceptorsHolder.register(interceptor);
        EnvelopeSentEventInterceptorsHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(interceptorCalled.get(), "Interceptor should have been called");
    }

    @Test
    void testUnregister() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeSentEventInterceptorsHolder.EnvelopeSentEventInterceptor() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventInterceptorsHolder.register(interceptor);
        EnvelopeSentEventInterceptorsHolder.unregister(interceptor);
        EnvelopeSentEventInterceptorsHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after unregistering");
    }

    @Test
    void testReset() {
        // Given
        AtomicBoolean interceptorCalled = new AtomicBoolean(false);
        Object testEnvelope = new Object();
        long testTimestamp = System.currentTimeMillis();

        var interceptor = new EnvelopeSentEventInterceptorsHolder.EnvelopeSentEventInterceptor() {
            @Override
            public void onEnvelopeSent(Object envelope, long timestamp) {
                interceptorCalled.set(true);
            }
        };

        // When
        EnvelopeSentEventInterceptorsHolder.register(interceptor);
        EnvelopeSentEventInterceptorsHolder.reset();
        EnvelopeSentEventInterceptorsHolder.onEnvelopeSent(testEnvelope, testTimestamp);

        // Then
        assertTrue(!interceptorCalled.get(), "Interceptor should not have been called after reset");
    }
}
