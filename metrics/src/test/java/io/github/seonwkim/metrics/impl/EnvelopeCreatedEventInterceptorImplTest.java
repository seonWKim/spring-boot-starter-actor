package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seonwkim.metrics.interceptor.EnvelopeCreatedEventInterceptorsHolder;

class EnvelopeCreatedEventInterceptorImplTest {

	private EnvelopeCreatedEventInterceptorImpl interceptor;

	@BeforeEach
	void setUp() {
		EnvelopeCreatedEventInterceptorsHolder.reset();
		interceptor = new EnvelopeCreatedEventInterceptorImpl();
		interceptor.reset();
		EnvelopeCreatedEventInterceptorsHolder.register(interceptor);
	}

	@Test
	void testEnvelopeCreationMetricsRecorded() {
		// Create a mock envelope with a message
		Object envelope = createMockEnvelope(new TestMessage("Hello"));
		long timestamp = System.nanoTime();

		// Simulate envelope creation event
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelope, timestamp);

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metrics = 
				interceptor.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Envelope metrics should be recorded");
		assertEquals(1, metrics.getCreatedCount(), "Should have created 1 envelope");
		assertEquals(timestamp, metrics.getFirstCreatedTimestamp(), "First created timestamp should match");
		assertEquals(timestamp, metrics.getLastCreatedTimestamp(), "Last created timestamp should match");
		assertEquals(0, metrics.getTimeBetweenFirstAndLast(), 
				"First and last timestamps should be equal for single message");
	}

	@Test
	void testMultipleEnvelopeCreations() throws Exception {
		// Create multiple mock envelopes
		long firstTimestamp = System.nanoTime();
		
		for (int i = 0; i < 5; i++) {
			Object envelope = createMockEnvelope(new TestMessage("Message " + i));
			long timestamp = firstTimestamp + (i * 1000000); // Add 1ms between each
			EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelope, timestamp);
		}

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metrics = 
				interceptor.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Envelope metrics should be recorded");
		assertEquals(5, metrics.getCreatedCount(), "Should have created 5 envelopes");
		assertEquals(firstTimestamp, metrics.getFirstCreatedTimestamp(), "First created timestamp should match");
		assertEquals(firstTimestamp + (4 * 1000000), metrics.getLastCreatedTimestamp(), "Last created timestamp should match");
		assertEquals(4 * 1000000, metrics.getTimeBetweenFirstAndLast(), 
				"Time between first and last should be 4ms in nanoseconds");
	}

	@Test
	void testDifferentMessageTypes() {
		// Create envelopes with different message types
		Object envelopeA1 = createMockEnvelope(new TypeA());
		Object envelopeB = createMockEnvelope(new TypeB());
		Object envelopeA2 = createMockEnvelope(new TypeA());
		
		long timestamp = System.nanoTime();
		
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelopeA1, timestamp);
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelopeB, timestamp + 1000);
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelopeA2, timestamp + 2000);

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics typeAMetrics = 
				interceptor.getEnvelopeMetrics("TypeA");
		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics typeBMetrics = 
				interceptor.getEnvelopeMetrics("TypeB");

		assertNotNull(typeAMetrics, "TypeA metrics should be recorded");
		assertEquals(2, typeAMetrics.getCreatedCount(), "Should have created 2 TypeA envelopes");

		assertNotNull(typeBMetrics, "TypeB metrics should be recorded");
		assertEquals(1, typeBMetrics.getCreatedCount(), "Should have created 1 TypeB envelope");
	}

	@Test
	void testMetricsReset() {
		// Create an envelope and record metrics
		Object envelope = createMockEnvelope(new TestMessage("Test"));
		long timestamp = System.nanoTime();
		
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelope, timestamp);

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metrics = 
				interceptor.getEnvelopeMetrics("TestMessage");
		assertNotNull(metrics, "Metrics should exist before reset");
		assertEquals(1, metrics.getCreatedCount(), "Should have 1 envelope created");

		// Reset metrics
		interceptor.reset();

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metricsAfterReset = 
				interceptor.getEnvelopeMetrics("TestMessage");
		assertNull(metricsAfterReset, "Metrics should be null after reset");
	}

	@Test
	void testTimeBetweenFirstAndLastWithSingleMessage() {
		// Create a single envelope
		Object envelope = createMockEnvelope(new TestMessage("Single"));
		long timestamp = System.nanoTime();
		
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelope, timestamp);

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metrics = 
				interceptor.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Metrics should be recorded");
		assertEquals(0, metrics.getTimeBetweenFirstAndLast(), 
				"Time between first and last should be 0 for single message");
	}

	@Test
	void testUnknownMessageType() {
		// Create an envelope without a proper message method
		Object envelope = new Object(); // This will result in "unknown" message type
		long timestamp = System.nanoTime();
		
		EnvelopeCreatedEventInterceptorsHolder.onEnvelopeCreated(envelope, timestamp);

		EnvelopeCreatedEventInterceptorImpl.EnvelopeMetrics metrics = 
				interceptor.getEnvelopeMetrics("unknown");

		assertNotNull(metrics, "Metrics should be recorded for unknown type");
		assertEquals(1, metrics.getCreatedCount(), "Should have created 1 envelope");
	}

	// Helper method to create a mock envelope
	private Object createMockEnvelope(Object message) {
		return new MockEnvelope(message);
	}

	// Mock envelope class that mimics the structure expected by the interceptor
	private static class MockEnvelope {
		private final Object message;

		public MockEnvelope(Object message) {
			this.message = message;
		}

		public Object message() {
			return message;
		}
	}

	// Test message classes
	private static class TestMessage {
		private final String text;

		public TestMessage(String text) {
			this.text = text;
		}
	}

	private static class TypeA {
	}

	private static class TypeB {
	}
}
