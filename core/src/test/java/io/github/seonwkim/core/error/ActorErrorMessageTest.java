package io.github.seonwkim.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Tests for ActorErrorMessage error detection and formatting.
 */
public class ActorErrorMessageTest {

    @Test
    public void testDetectBeanNotFoundError() {
        BeanCreationException error = new BeanCreationException("orderRepository not found");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.BEAN_NOT_FOUND);
    }

    @Test
    public void testDetectNoSuchBeanDefinitionError() {
        NoSuchBeanDefinitionException error =
                new NoSuchBeanDefinitionException("orderRepository");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.BEAN_NOT_FOUND);
    }

    @Test
    public void testDetectTimeoutError() {
        TimeoutException error = new TimeoutException("Request timed out");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.TIMEOUT);
    }

    @Test
    public void testDetectConfigurationError() {
        IllegalArgumentException error = new IllegalArgumentException("Invalid configuration");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.CONFIGURATION_ERROR);
    }

    @Test
    public void testDetectActorSpawnFailure() {
        RuntimeException error = new RuntimeException("Failed to spawn actor");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.ACTOR_SPAWN_FAILURE);
    }

    @Test
    public void testDetectDeadLetterError() {
        IllegalStateException error = new IllegalStateException("Message sent to dead letter");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.DEAD_LETTER);
    }

    @Test
    public void testDetectSerializationError() {
        RuntimeException error = new RuntimeException("Failed to serialize message");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.SERIALIZATION_ERROR);
    }

    @Test
    public void testDetectClusterFormationError() {
        RuntimeException error = new RuntimeException("Cluster formation failed");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.CLUSTER_FORMATION);
    }

    @Test
    public void testDetectClusterUnreachableError() {
        RuntimeException error = new RuntimeException("Cluster node unreachable");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.CLUSTER_UNREACHABLE);
    }

    @Test
    public void testDetectShardingError() {
        RuntimeException error = new RuntimeException("Sharding region not started");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.SHARDING_NOT_STARTED);
    }

    @Test
    public void testDetectSupervisionError() {
        RuntimeException error = new RuntimeException("Supervision strategy failed");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.SUPERVISION_ERROR);
    }

    @Test
    public void testDetectUnknownError() {
        RuntimeException error = new RuntimeException("Some generic error");
        ErrorType type = ActorErrorMessage.detectErrorType(error);
        assertThat(type).isEqualTo(ErrorType.UNKNOWN);
    }

    @Test
    public void testFormatCompactMessage() {
        BeanCreationException error = new BeanCreationException("orderRepository not found");
        String formatted = ActorErrorMessage.formatCompact(error);

        assertThat(formatted).contains("BeanCreationException");
        assertThat(formatted).contains("orderRepository not found");
        assertThat(formatted).contains("💡 Hint:");
        assertThat(formatted).contains("📖 Docs:");
    }

    @Test
    public void testFormatVerboseMessage() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("OrderActor")
                        .actorPath("/user/order-actor")
                        .messageType("CreateOrder")
                        .build();

        BeanCreationException error = new BeanCreationException("orderRepository not found");
        String formatted = ActorErrorMessage.formatVerbose(error, context);

        assertThat(formatted).contains("BeanCreationException");
        assertThat(formatted).contains("orderRepository not found");
        assertThat(formatted).contains("💡 Troubleshooting hints:");
        assertThat(formatted).contains("📖 Documentation:");
        assertThat(formatted).contains("🎯 Actor Context:");
        assertThat(formatted).contains("/user/order-actor");
        assertThat(formatted).contains("CreateOrder");
    }

    @Test
    public void testFormatWithContext() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/my-actor")
                        .messageType("MyMessage")
                        .additionalInfo("currentTimeout", "3s")
                        .build();

        TimeoutException error = new TimeoutException("Ask timeout");
        String formatted = ActorErrorMessage.format(error, context);

        assertThat(formatted).contains("TimeoutException");
        assertThat(formatted).contains("Ask timeout");
        assertThat(formatted).contains("🎯 Actor: /user/my-actor");
        assertThat(formatted).contains("📨 Message: MyMessage");
    }

    @Test
    public void testFormatWithNullMessage() {
        RuntimeException error = new RuntimeException((String) null);
        String formatted = ActorErrorMessage.formatCompact(error);

        assertThat(formatted).contains("RuntimeException");
        assertThat(formatted).contains("No message");
    }

    @Test
    public void testFormatWithCause() {
        BeanCreationException cause = new BeanCreationException("orderRepository not found");
        RuntimeException error = new RuntimeException("Failed to spawn actor", cause);

        String formatted = ActorErrorMessage.formatVerbose(error, null);

        assertThat(formatted).contains("RuntimeException");
        assertThat(formatted).contains("Failed to spawn actor");
        assertThat(formatted).contains("Cause:");
        assertThat(formatted).contains("orderRepository not found");
    }

    @Test
    public void testFormatWithNullError() {
        ErrorType type = ActorErrorMessage.detectErrorType(null);
        assertThat(type).isEqualTo(ErrorType.UNKNOWN);
    }
}
