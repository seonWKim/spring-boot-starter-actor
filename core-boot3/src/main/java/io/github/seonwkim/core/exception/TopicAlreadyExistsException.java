package io.github.seonwkim.core.exception;

/**
 * Exception thrown when attempting to create a topic that already exists.
 *
 * <p>This exception is thrown when using {@code .create()} on a topic that has already been
 * created. If you want idempotent topic creation, use {@code .getOrCreate()} instead.
 */
public class TopicAlreadyExistsException extends RuntimeException {

    private final String topicName;
    private final Class<?> messageType;

    /**
     * Creates a new TopicAlreadyExistsException.
     *
     * @param topicName The name of the topic that already exists
     * @param messageType The message type of the topic
     */
    public TopicAlreadyExistsException(String topicName, Class<?> messageType) {
        super(String.format(
                "Topic '%s' with message type '%s' already exists. Use getOrCreate() for idempotent creation.",
                topicName, messageType.getName()));
        this.topicName = topicName;
        this.messageType = messageType;
    }

    /**
     * Creates a new TopicAlreadyExistsException with a custom message.
     *
     * @param topicName The name of the topic that already exists
     * @param messageType The message type of the topic
     * @param message Custom error message
     */
    public TopicAlreadyExistsException(String topicName, Class<?> messageType, String message) {
        super(message);
        this.topicName = topicName;
        this.messageType = messageType;
    }

    /**
     * Gets the name of the topic that already exists.
     *
     * @return the topic name
     */
    public String getTopicName() {
        return topicName;
    }

    /**
     * Gets the message type of the topic that already exists.
     *
     * @return the message type
     */
    public Class<?> getMessageType() {
        return messageType;
    }
}
