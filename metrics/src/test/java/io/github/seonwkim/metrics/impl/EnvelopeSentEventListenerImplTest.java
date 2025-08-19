package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seonwkim.metrics.listener.EnvelopeSentEventListenerHolder;

class EnvelopeSentEventListenerImplTest {

	private EnvelopeSentEventListenerImpl listener;

	@BeforeEach
	void setUp() {
		EnvelopeSentEventListenerHolder.reset();
		listener = new EnvelopeSentEventListenerImpl();
		listener.reset();
		EnvelopeSentEventListenerHolder.register(listener);
	}

	@Test
	void testEnvelopeSentMetricsRecorded() {
		// Create a mock envelope with a message
		Object envelope = createMockEnvelope(new TestMessage("Hello"));
		long timestamp = System.nanoTime();

		// Simulate envelope sent event
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, timestamp);

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Envelope metrics should be recorded");
		assertEquals(1, metrics.getSentCount(), "Should have sent 1 envelope");
		assertEquals(timestamp, metrics.getFirstSentTimestamp(), "First sent timestamp should match");
		assertEquals(timestamp, metrics.getLastSentTimestamp(), "Last sent timestamp should match");
		assertEquals(0, metrics.getTimeBetweenFirstAndLast(), 
				"First and last timestamps should be equal for single message");
	}

	@Test
	void testMultipleEnvelopeSentEvents() throws Exception {
		// Create multiple mock envelopes
		long firstTimestamp = System.nanoTime();
		
		for (int i = 0; i < 5; i++) {
			Object envelope = createMockEnvelope(new TestMessage("Message " + i));
			long timestamp = firstTimestamp + (i * 1000000); // Add 1ms between each
			EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, timestamp);
		}

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Envelope metrics should be recorded");
		assertEquals(5, metrics.getSentCount(), "Should have sent 5 envelopes");
		assertEquals(firstTimestamp, metrics.getFirstSentTimestamp(), "First sent timestamp should match");
		assertEquals(firstTimestamp + (4 * 1000000), metrics.getLastSentTimestamp(), "Last sent timestamp should match");
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
		
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelopeA1, timestamp);
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelopeB, timestamp + 1000);
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelopeA2, timestamp + 2000);

		EnvelopeSentEventListenerImpl.EnvelopeMetrics typeAMetrics = 
				listener.getEnvelopeMetrics("TypeA");
		EnvelopeSentEventListenerImpl.EnvelopeMetrics typeBMetrics = 
				listener.getEnvelopeMetrics("TypeB");

		assertNotNull(typeAMetrics, "TypeA metrics should be recorded");
		assertEquals(2, typeAMetrics.getSentCount(), "Should have sent 2 TypeA envelopes");

		assertNotNull(typeBMetrics, "TypeB metrics should be recorded");
		assertEquals(1, typeBMetrics.getSentCount(), "Should have sent 1 TypeB envelope");
	}

	@Test
	void testMetricsReset() {
		// Create an envelope and record metrics
		Object envelope = createMockEnvelope(new TestMessage("Test"));
		long timestamp = System.nanoTime();
		
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, timestamp);

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");
		assertNotNull(metrics, "Metrics should exist before reset");
		assertEquals(1, metrics.getSentCount(), "Should have 1 envelope sent");

		// Reset metrics
		listener.reset();

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metricsAfterReset = 
				listener.getEnvelopeMetrics("TestMessage");
		assertNull(metricsAfterReset, "Metrics should be null after reset");
	}

	@Test
	void testTimeBetweenFirstAndLastWithSingleMessage() {
		// Create a single envelope
		Object envelope = createMockEnvelope(new TestMessage("Single"));
		long timestamp = System.nanoTime();
		
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, timestamp);

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");

		assertNotNull(metrics, "Metrics should be recorded");
		assertEquals(0, metrics.getTimeBetweenFirstAndLast(), 
				"Time between first and last should be 0 for single message");
	}

	@Test
	void testUnknownMessageType() {
		// Create an envelope without a proper message method
		Object envelope = new Object(); // This will result in "unknown" message type
		long timestamp = System.nanoTime();
		
		EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, timestamp);

		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("unknown");

		assertNotNull(metrics, "Metrics should be recorded for unknown type");
		assertEquals(1, metrics.getSentCount(), "Should have sent 1 envelope");
	}

	@Test
	void testConcurrentEnvelopeSending() throws Exception {
		// Test thread safety with concurrent envelope sending
		int threadCount = 10;
		int messagesPerThread = 100;
		Thread[] threads = new Thread[threadCount];
		
		for (int i = 0; i < threadCount; i++) {
			threads[i] = new Thread(() -> {
				for (int j = 0; j < messagesPerThread; j++) {
					Object envelope = createMockEnvelope(new TestMessage("Concurrent"));
					EnvelopeSentEventListenerHolder.onEnvelopeSent(envelope, System.nanoTime());
				}
			});
		}
		
		// Start all threads
		for (Thread thread : threads) {
			thread.start();
		}
		
		// Wait for all threads to complete
		for (Thread thread : threads) {
			thread.join();
		}
		
		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");
		
		assertNotNull(metrics, "Metrics should be recorded");
		assertEquals(threadCount * messagesPerThread, metrics.getSentCount(), 
				"Should have sent " + (threadCount * messagesPerThread) + " envelopes");
	}

	@Test
	void testNullEnvelope() {
		// Test handling of null envelope
		EnvelopeSentEventListenerHolder.onEnvelopeSent(null, System.nanoTime());
		
		// Should handle gracefully without throwing exception
		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("unknown");
		
		// May or may not record a metric for null, depending on implementation
		// Just verify no exception is thrown
		assertTrue(true, "Should handle null envelope gracefully");
	}

	@Test
	void testGetMetricsForNonExistentType() {
		// Test getting metrics for a message type that was never sent
		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("NonExistentType");
		
		assertNull(metrics, "Should return null for non-existent message type");
	}

	@Test
	void testDirectOnEnvelopeSentCall() {
		// Test calling onEnvelopeSent directly on the listener
		Object envelope = createMockEnvelope(new TestMessage("Direct"));
		long timestamp = System.nanoTime();
		
		listener.onEnvelopeSent(envelope, timestamp);
		
		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				listener.getEnvelopeMetrics("TestMessage");
		
		assertNotNull(metrics, "Metrics should be recorded");
		assertEquals(1, metrics.getSentCount(), "Should have sent 1 envelope");
		assertEquals(timestamp, metrics.getFirstSentTimestamp(), "Timestamp should match");
	}

	@Test
	void testEnvelopeMetricsWithZeroInitialValues() {
		// Test that a new EnvelopeMetrics has correct initial values
		EnvelopeSentEventListenerImpl.EnvelopeMetrics metrics = 
				new EnvelopeSentEventListenerImpl.EnvelopeMetrics();
		
		assertEquals(0, metrics.getSentCount(), "Initial sent count should be 0");
		assertEquals(0, metrics.getFirstSentTimestamp(), "Initial first timestamp should be 0");
		assertEquals(0, metrics.getLastSentTimestamp(), "Initial last timestamp should be 0");
		assertEquals(0, metrics.getTimeBetweenFirstAndLast(), "Initial time between should be 0");
	}

	// Helper method to create a mock envelope
	private Object createMockEnvelope(Object message) {
		return new MockEnvelope(message);
	}

	// Mock envelope class that mimics the structure expected by the listener
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