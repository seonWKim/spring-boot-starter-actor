package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.ActorMetricsRegistry;
import io.github.seonwkim.metrics.EnvelopeInstrumentation;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive interceptor for message processing events that tracks all message-related metrics.
 *
 * <p>This interceptor captures:
 * - Message processing time
 * - Time-in-mailbox (from envelope creation to processing)
 * - Messages processed count
 * - Error count
 * - Metrics by message type
 * - Metrics by actor path
 */
public class ComprehensiveInvokeAdviceInterceptor
        implements InvokeAdviceEventInterceptorsHolder.InvokeAdviceEventInterceptor {
    private static final Logger logger =
            LoggerFactory.getLogger(ComprehensiveInvokeAdviceInterceptor.class);

    private final ActorMetricsRegistry metricsRegistry = ActorMetricsRegistry.getInstance();

    @Override
    public void onEnter(Object envelope) {
        // Nothing needed on enter
    }

    @Override
    public void onExit(Object envelope, long startTime) {
        try {
            long endTime = System.nanoTime();
            long processingTimeNanos = endTime - startTime;

            // Extract message type and actor path
            String messageType = getMessageType(envelope);
            String actorPath = getActorPath(envelope);

            // Record processing time
            metricsRegistry.recordProcessingTime(messageType, processingTimeNanos);

            // Record message processed
            metricsRegistry.recordMessageProcessed(messageType);

            // Calculate time in mailbox
            long envelopeCreationTime = EnvelopeInstrumentation.getEnvelopeTimestamp(envelope);
            long timeInMailboxNanos = startTime - envelopeCreationTime;
            if (timeInMailboxNanos > 0) {
                metricsRegistry.recordTimeInMailbox(actorPath, timeInMailboxNanos);
            }

            logger.debug(
                    "Message {} processed in {} ms, time in mailbox: {} ms, actor: {}",
                    messageType,
                    processingTimeNanos / 1_000_000,
                    timeInMailboxNanos / 1_000_000,
                    actorPath);
        } catch (Exception e) {
            logger.error("Error recording message processing metrics", e);
        }
    }

    @Override
    public void onError(Object envelope, long startTime, Throwable throwable) {
        try {
            String messageType = getMessageType(envelope);
            String errorType = throwable.getClass().getSimpleName();

            // Record error
            metricsRegistry.recordProcessingError(messageType, errorType);

            logger.debug("Message {} processing error: {}", messageType, errorType);
        } catch (Exception e) {
            logger.error("Error recording processing error metrics", e);
        }
    }

    private String getMessageType(Object envelope) {
        try {
            Object message = envelope.getClass().getMethod("message").invoke(envelope);
            return message.getClass().getSimpleName();
        } catch (Exception e) {
            logger.debug("Could not extract message type from envelope", e);
            return "unknown";
        }
    }

    private String getActorPath(Object envelope) {
        try {
            Object receiver = envelope.getClass().getMethod("receiver").invoke(envelope);
            Object path = receiver.getClass().getMethod("path").invoke(receiver);
            return path.toString();
        } catch (Exception e) {
            logger.debug("Could not extract actor path from envelope", e);
            return "unknown";
        }
    }
}
