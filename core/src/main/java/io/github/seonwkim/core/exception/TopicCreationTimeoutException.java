package io.github.seonwkim.core.exception;

import java.time.Duration;

/**
 * Exception thrown when topic creation times out.
 *
 * <p>This exception is thrown when the ask pattern times out while waiting for the RootGuardian
 * to create a topic. This typically indicates system overload or network issues in a cluster.
 */
public class TopicCreationTimeoutException extends RuntimeException {

    private final String topicName;
    private final Class<?> messageType;
    private final Duration timeout;

    /**
     * Creates a new TopicCreationTimeoutException.
     *
     * @param topicName The name of the topic that failed to create
     * @param messageType The message type of the topic
     * @param timeout The timeout duration that was exceeded
     */
    public TopicCreationTimeoutException(String topicName, Class<?> messageType, Duration timeout) {
        super(String.format(
                "Topic creation timed out after %s for topic '%s' with message type '%s'",
                timeout, topicName, messageType.getName()));
        this.topicName = topicName;
        this.messageType = messageType;
        this.timeout = timeout;
    }

    /**
     * Creates a new TopicCreationTimeoutException with a cause.
     *
     * @param topicName The name of the topic that failed to create
     * @param messageType The message type of the topic
     * @param timeout The timeout duration that was exceeded
     * @param cause The underlying cause
     */
    public TopicCreationTimeoutException(
            String topicName, Class<?> messageType, Duration timeout, Throwable cause) {
        super(
                String.format(
                        "Topic creation timed out after %s for topic '%s' with message type '%s'",
                        timeout, topicName, messageType.getName()),
                cause);
        this.topicName = topicName;
        this.messageType = messageType;
        this.timeout = timeout;
    }

    /**
     * Gets the name of the topic that failed to create.
     *
     * @return the topic name
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Gets the message type of the topic that failed to create.
     *
     * @return the message type
     */
    public Class<?> getMessageType() {
        return messageType;
    }

    /**
     * Gets the timeout duration that was exceeded.
     *
     * @return the timeout duration
     */
    public Duration getTimeout() {
        return timeout;
    }
}
