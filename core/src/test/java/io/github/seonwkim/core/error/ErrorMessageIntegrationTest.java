package io.github.seonwkim.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;

/**
 * Integration tests demonstrating real-world usage of the error message framework.
 * These tests show what the error messages actually look like.
 */
public class ErrorMessageIntegrationTest {

    @Test
    public void demonstrateBeanNotFoundError() {
        // Simulate a common Spring Bean not found error
        BeanCreationException error = new BeanCreationException("orderRepository not found");

        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("OrderActor")
                        .actorPath("/user/order-actor")
                        .build();

        String compactMessage = ActorErrorMessage.format(error, context, false);
        String verboseMessage = ActorErrorMessage.format(error, context, true);

        // Compact message should include:
        assertThat(compactMessage).contains("BeanCreationException");
        assertThat(compactMessage).contains("orderRepository not found");
        assertThat(compactMessage).contains("💡 Hint:");
        assertThat(compactMessage).contains("@Component");
        assertThat(compactMessage).contains("📖 Docs:");

        // Verbose message should include:
        assertThat(verboseMessage).contains("╭─");
        assertThat(verboseMessage).contains("💡 Troubleshooting hints:");
        assertThat(verboseMessage).contains("OrderActor");
        assertThat(verboseMessage).contains("🎯 Actor Context:");
        assertThat(verboseMessage).contains("/user/order-actor");

        // Print for visual inspection (will appear in test output)
        System.out.println("\n=== Bean Not Found Error (Compact) ===");
        System.out.println(compactMessage);
        System.out.println("\n=== Bean Not Found Error (Verbose) ===");
        System.out.println(verboseMessage);
    }

    @Test
    public void demonstrateTimeoutError() {
        TimeoutException error = new TimeoutException("Ask timeout after 3 seconds");

        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/payment-actor")
                        .messageType("ProcessPayment")
                        .additionalInfo("currentTimeout", "3s")
                        .build();

        String message = ActorErrorMessage.formatVerbose(error, context);

        assertThat(message).contains("TimeoutException");
        assertThat(message).contains("Ask timeout");
        assertThat(message).contains("💡 Troubleshooting hints:");
        assertThat(message).contains("3s"); // From context substitution
        assertThat(message).contains("/user/payment-actor");
        assertThat(message).contains("ProcessPayment");

        System.out.println("\n=== Timeout Error ===");
        System.out.println(message);
    }

    @Test
    public void demonstrateClusterFormationError() {
        RuntimeException error = new RuntimeException("Cluster formation failed: seed nodes unreachable");

        String message = ActorErrorMessage.formatVerbose(error, null);

        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("Cluster formation failed");
        assertThat(message).contains("💡 Troubleshooting hints:");
        assertThat(message).contains("seed node");
        assertThat(message).contains("network");

        System.out.println("\n=== Cluster Formation Error ===");
        System.out.println(message);
    }

    @Test
    public void demonstrateSerializationError() {
        RuntimeException error =
                new RuntimeException(
                        "Failed to serialize message: OrderMessage contains non-serializable field");

        ErrorContext context = ErrorContext.builder().messageType("OrderMessage").build();

        String message = ActorErrorMessage.format(error, context, false);

        assertThat(message).contains("Failed to serialize");
        assertThat(message).contains("💡 Hint:");
        assertThat(message).contains("JsonSerializable");
        assertThat(message).contains("📨 Message: OrderMessage");

        System.out.println("\n=== Serialization Error ===");
        System.out.println(message);
    }

    @Test
    public void demonstrateMultipleErrorScenarios() {
        System.out.println("\n=== Multiple Error Scenarios Demo ===");

        // 1. Configuration Error
        IllegalArgumentException configError =
                new IllegalArgumentException("Invalid port configuration in application.yml");
        System.out.println("\n1. Configuration Error:");
        System.out.println(ActorErrorMessage.formatCompact(configError));

        // 2. Dead Letter
        ErrorContext deadLetterContext =
                ErrorContext.builder().actorPath("/user/stopped-actor").build();
        RuntimeException deadLetterError =
                new RuntimeException("Message sent to dead letter: actor stopped");
        System.out.println("\n2. Dead Letter Error:");
        System.out.println(ActorErrorMessage.format(deadLetterError, deadLetterContext, false));

        // 3. Actor Spawn Failure
        ErrorContext spawnContext = ErrorContext.builder().actorClass("UserActor").build();
        RuntimeException spawnError =
                new RuntimeException("Failed to spawn actor: constructor threw exception");
        System.out.println("\n3. Actor Spawn Failure:");
        System.out.println(ActorErrorMessage.format(spawnError, spawnContext, false));

        // 4. Supervision Error
        RuntimeException supervisionError =
                new RuntimeException("Supervision strategy failed: restart limit exceeded");
        System.out.println("\n4. Supervision Error:");
        System.out.println(ActorErrorMessage.formatCompact(supervisionError));

        // All assertions to verify the framework is working
        assertThat(ActorErrorMessage.detectErrorType(configError))
                .isEqualTo(ErrorType.CONFIGURATION_ERROR);
        assertThat(ActorErrorMessage.detectErrorType(deadLetterError))
                .isEqualTo(ErrorType.DEAD_LETTER);
        assertThat(ActorErrorMessage.detectErrorType(spawnError))
                .isEqualTo(ErrorType.ACTOR_SPAWN_FAILURE);
        assertThat(ActorErrorMessage.detectErrorType(supervisionError))
                .isEqualTo(ErrorType.SUPERVISION_ERROR);
    }
}
