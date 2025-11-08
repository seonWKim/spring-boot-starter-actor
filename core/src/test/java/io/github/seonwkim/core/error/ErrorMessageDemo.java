package io.github.seonwkim.core.error;

import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Demonstration of the enhanced error message framework.
 * This class shows how to use the error framework to produce helpful error messages.
 *
 * <p>Run this class to see examples of error messages in different formats.
 */
public class ErrorMessageDemo {

    public static void main(String[] args) {
        System.out.println("=== Enhanced Error Message Framework Demo ===\n");

        demonstrateCompactFormat();
        demonstrateVerboseFormat();
        demonstrateWithContext();
        demonstrateDifferentErrorTypes();
    }

    private static void demonstrateCompactFormat() {
        System.out.println("1. COMPACT FORMAT (Production Mode)");
        System.out.println("=====================================");

        BeanCreationException error = new BeanCreationException("orderRepository not found");
        String message = ActorErrorMessage.formatCompact(error);

        System.out.println(message);
        System.out.println("\n");
    }

    private static void demonstrateVerboseFormat() {
        System.out.println("2. VERBOSE FORMAT (Development Mode)");
        System.out.println("=====================================");

        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("OrderActor")
                        .actorPath("/user/order-actor")
                        .messageType("CreateOrder")
                        .actorState("INITIALIZING")
                        .build();

        BeanCreationException error = new BeanCreationException("orderRepository not found");
        String message = ActorErrorMessage.formatVerbose(error, context);

        System.out.println(message);
        System.out.println("\n");
    }

    private static void demonstrateWithContext() {
        System.out.println("3. TIMEOUT ERROR WITH CONTEXT");
        System.out.println("==============================");

        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/payment-actor")
                        .messageType("ProcessPayment")
                        .additionalInfo("currentTimeout", "3s")
                        .additionalInfo("mailboxSize", "150")
                        .build();

        TimeoutException error = new TimeoutException("Ask timeout after 3 seconds");
        String message = ActorErrorMessage.formatVerbose(error, context);

        System.out.println(message);
        System.out.println("\n");
    }

    private static void demonstrateDifferentErrorTypes() {
        System.out.println("4. DIFFERENT ERROR TYPES");
        System.out.println("=========================");

        // Configuration Error
        System.out.println("A. Configuration Error:");
        System.out.println("-----------------------");
        IllegalArgumentException configError =
                new IllegalArgumentException("Invalid port configuration");
        System.out.println(ActorErrorMessage.formatCompact(configError));
        System.out.println();

        // Dead Letter Error
        System.out.println("B. Dead Letter Error:");
        System.out.println("---------------------");
        ErrorContext deadLetterContext =
                ErrorContext.builder().actorPath("/user/stopped-actor").build();
        RuntimeException deadLetterError =
                new RuntimeException("Message sent to dead letter: actor stopped");
        System.out.println(ActorErrorMessage.format(deadLetterError, deadLetterContext, false));
        System.out.println();

        // Serialization Error
        System.out.println("C. Serialization Error:");
        System.out.println("-----------------------");
        ErrorContext serializationContext =
                ErrorContext.builder().messageType("OrderMessage").build();
        RuntimeException serializationError =
                new RuntimeException("Failed to serialize message for cluster transport");
        System.out.println(
                ActorErrorMessage.format(serializationError, serializationContext, false));
        System.out.println();

        // Cluster Formation Error
        System.out.println("D. Cluster Formation Error:");
        System.out.println("----------------------------");
        RuntimeException clusterError = new RuntimeException("Cluster formation failed");
        System.out.println(ActorErrorMessage.formatCompact(clusterError));
        System.out.println();

        // Actor Spawn Failure
        System.out.println("E. Actor Spawn Failure:");
        System.out.println("-----------------------");
        ErrorContext spawnContext = ErrorContext.builder().actorClass("UserActor").build();
        RuntimeException spawnError = new RuntimeException("Failed to spawn actor");
        System.out.println(ActorErrorMessage.format(spawnError, spawnContext, false));
        System.out.println();
    }
}
