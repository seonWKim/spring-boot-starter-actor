package io.github.seonwkim.core.error;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Main entry point for formatting enhanced error messages with troubleshooting hints.
 * Automatically detects error types and provides context-aware guidance.
 */
public class ActorErrorMessage {

    private static final TroubleshootingHints hints = TroubleshootingHints.getInstance();

    /**
     * Formats an error with enhanced troubleshooting information.
     *
     * @param error The throwable to format
     * @param context Optional context information
     * @return Formatted error message
     */
    public static String format(Throwable error, @Nullable ErrorContext context) {
        return format(error, context, false);
    }

    /**
     * Formats an error with enhanced troubleshooting information.
     *
     * @param error The throwable to format
     * @param context Optional context information
     * @param verboseMode Whether to use verbose formatting
     * @return Formatted error message
     */
    public static String format(
            Throwable error, @Nullable ErrorContext context, boolean verboseMode) {
        ErrorType errorType = detectErrorType(error);
        List<String> troubleshootingHints = hints.getHints(errorType, context);
        String docLink = hints.getDocumentationLink(errorType);

        return new ErrorMessageBuilder(error)
                .addHints(troubleshootingHints)
                .addDocumentationLink(docLink)
                .withContext(context)
                .verboseMode(verboseMode)
                .build();
    }

    /**
     * Formats an error message in compact mode (production).
     *
     * @param error The throwable to format
     * @return Compact formatted error message
     */
    public static String formatCompact(Throwable error) {
        return format(error, null, false);
    }

    /**
     * Formats an error message in verbose mode (development).
     *
     * @param error The throwable to format
     * @param context Optional context information
     * @return Verbose formatted error message
     */
    public static String formatVerbose(Throwable error, @Nullable ErrorContext context) {
        return format(error, context, true);
    }

    /**
     * Detects the error type from the exception.
     *
     * @param error The throwable to analyze
     * @return The detected error type
     */
    public static ErrorType detectErrorType(Throwable error) {
        if (error == null) {
            return ErrorType.UNKNOWN;
        }

        // Check by exception type first
        if (error instanceof BeanCreationException
                || error instanceof NoSuchBeanDefinitionException) {
            return ErrorType.BEAN_NOT_FOUND;
        }

        if (error instanceof TimeoutException) {
            return ErrorType.TIMEOUT;
        }

        // Check message for more specific error types
        ErrorType messageBasedType = detectFromMessage(error);
        if (messageBasedType != ErrorType.UNKNOWN) {
            return messageBasedType;
        }

        // Check cause if message didn't help
        if (error.getCause() != null) {
            ErrorType causeType = detectErrorType(error.getCause());
            if (causeType != ErrorType.UNKNOWN) {
                return causeType;
            }
        }

        return ErrorType.UNKNOWN;
    }

    private static ErrorType detectFromMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return ErrorType.UNKNOWN;
        }

        String lowerMessage = message.toLowerCase();

        // Configuration errors
        if (lowerMessage.contains("configuration")
                || lowerMessage.contains("property")
                || lowerMessage.contains("invalid")) {
            return ErrorType.CONFIGURATION_ERROR;
        }

        // Actor spawn failures
        if (lowerMessage.contains("spawn") || lowerMessage.contains("create actor")) {
            return ErrorType.ACTOR_SPAWN_FAILURE;
        }

        // Timeout errors
        if (lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return ErrorType.TIMEOUT;
        }

        // Dead letter
        if (lowerMessage.contains("dead letter") || lowerMessage.contains("stopped actor")) {
            return ErrorType.DEAD_LETTER;
        }

        // Serialization
        if (lowerMessage.contains("serializ") || lowerMessage.contains("deserializ")) {
            return ErrorType.SERIALIZATION_ERROR;
        }

        // Cluster errors
        if (lowerMessage.contains("cluster")) {
            if (lowerMessage.contains("unreachable")) {
                return ErrorType.CLUSTER_UNREACHABLE;
            } else if (lowerMessage.contains("split brain")) {
                return ErrorType.CLUSTER_SPLIT_BRAIN;
            } else {
                return ErrorType.CLUSTER_FORMATION;
            }
        }

        // Sharding errors
        if (lowerMessage.contains("shard") || lowerMessage.contains("sharding")) {
            return ErrorType.SHARDING_NOT_STARTED;
        }

        // Supervision errors
        if (lowerMessage.contains("supervis") || lowerMessage.contains("restart")) {
            return ErrorType.SUPERVISION_ERROR;
        }

        return ErrorType.UNKNOWN;
    }
}
