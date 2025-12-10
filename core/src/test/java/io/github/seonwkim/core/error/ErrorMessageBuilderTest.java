package io.github.seonwkim.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for ErrorMessageBuilder formatting functionality.
 */
public class ErrorMessageBuilderTest {

    @Test
    public void testBuildCompactMessage() {
        RuntimeException error = new RuntimeException("Something went wrong");
        String message = new ErrorMessageBuilder(error).build();

        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("Something went wrong");
    }

    @Test
    public void testBuildCompactMessageWithHints() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .addHints(List.of("Hint 1", "Hint 2", "Hint 3"))
                        .build();

        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("💡 Hint:");
        assertThat(message).contains("Hint 1"); // Only first hint in compact mode
        assertThat(message).doesNotContain("Hint 2"); // Other hints not in compact mode
    }

    @Test
    public void testBuildCompactMessageWithDocLink() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .addDocumentationLink("https://docs.example.com/error")
                        .build();

        assertThat(message).contains("📖 Docs:");
        assertThat(message).contains("https://docs.example.com/error");
    }

    @Test
    public void testBuildCompactMessageWithContext() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/my-actor")
                        .messageType("MyMessage")
                        .build();

        RuntimeException error = new RuntimeException("Error occurred");
        String message = new ErrorMessageBuilder(error).withContext(context).build();

        assertThat(message).contains("🎯 Actor: /user/my-actor");
        assertThat(message).contains("📨 Message: MyMessage");
    }

    @Test
    public void testBuildVerboseMessage() {
        RuntimeException error = new RuntimeException("Something went wrong");
        String message = new ErrorMessageBuilder(error).verboseMode(true).build();

        assertThat(message).contains("╭─");
        assertThat(message).contains("╯");
        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("Something went wrong");
    }

    @Test
    public void testBuildVerboseMessageWithAllHints() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .addHints(List.of("Hint 1", "Hint 2", "Hint 3"))
                        .verboseMode(true)
                        .build();

        assertThat(message).contains("💡 Troubleshooting hints:");
        assertThat(message).contains("1. Hint 1");
        assertThat(message).contains("2. Hint 2");
        assertThat(message).contains("3. Hint 3");
    }

    @Test
    public void testBuildVerboseMessageWithContext() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/my-actor")
                        .actorClass("MyActor")
                        .messageType("MyMessage")
                        .actorState("RUNNING")
                        .build();

        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error).withContext(context).verboseMode(true).build();

        assertThat(message).contains("🎯 Actor Context:");
        assertThat(message).contains("Actor Path: /user/my-actor");
        assertThat(message).contains("Message Type: MyMessage");
        assertThat(message).contains("Actor State: RUNNING");
        assertThat(message).contains("Actor Class: MyActor");
    }

    @Test
    public void testBuildVerboseMessageWithDocLink() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .addDocumentationLink("https://docs.example.com/error")
                        .verboseMode(true)
                        .build();

        assertThat(message).contains("📖 Documentation:");
        assertThat(message).contains("https://docs.example.com/error");
    }

    @Test
    public void testBuildWithStackTrace() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .includeStackTrace(true)
                        .verboseMode(true)
                        .build();

        assertThat(message).contains("🔍 Stack trace:");
        assertThat(message).contains("java.lang.RuntimeException");
    }

    @Test
    public void testBuildWithoutStackTrace() {
        RuntimeException error = new RuntimeException("Error occurred");
        String message =
                new ErrorMessageBuilder(error)
                        .includeStackTrace(false)
                        .verboseMode(false)
                        .build();

        assertThat(message).doesNotContain("🔍 Stack trace:");
        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("Error occurred");
    }

    @Test
    public void testBuildWithCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        RuntimeException error = new RuntimeException("Error occurred", cause);
        String message = new ErrorMessageBuilder(error).verboseMode(true).build();

        assertThat(message).contains("Cause:");
        assertThat(message).contains("Root cause");
    }

    @Test
    public void testBuildWithNullMessage() {
        RuntimeException error = new RuntimeException((String) null);
        String message = new ErrorMessageBuilder(error).build();

        assertThat(message).contains("RuntimeException");
        assertThat(message).contains("No message");
    }

    @Test
    public void testBuildWithNullCauseMessage() {
        RuntimeException cause = new RuntimeException((String) null);
        RuntimeException error = new RuntimeException("Error occurred", cause);
        String message = new ErrorMessageBuilder(error).verboseMode(true).build();

        assertThat(message).contains("Cause:");
        assertThat(message).contains("No message");
    }

    @Test
    public void testPadRightTruncatesLongText() {
        RuntimeException error = new RuntimeException("A".repeat(100));
        String message = new ErrorMessageBuilder(error).verboseMode(true).build();

        assertThat(message).contains("RuntimeException");
        // Should be truncated and contain "..."
        assertThat(message).contains("...");
    }
}
