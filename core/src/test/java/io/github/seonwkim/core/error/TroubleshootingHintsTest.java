package io.github.seonwkim.core.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for TroubleshootingHints hint retrieval and substitution.
 */
public class TroubleshootingHintsTest {

    private final TroubleshootingHints hints = TroubleshootingHints.getInstance();

    @Test
    public void testGetHintsForBeanNotFound() {
        List<String> hintList = hints.getHints(ErrorType.BEAN_NOT_FOUND, null);

        assertThat(hintList).isNotEmpty();
        assertThat(hintList).anyMatch(hint -> hint.contains("@Component"));
        assertThat(hintList).anyMatch(hint -> hint.contains("component scanning"));
    }

    @Test
    public void testGetHintsForTimeout() {
        List<String> hintList = hints.getHints(ErrorType.TIMEOUT, null);

        assertThat(hintList).isNotEmpty();
        assertThat(hintList).anyMatch(hint -> hint.contains("timeout"));
        assertThat(hintList).anyMatch(hint -> hint.contains("mailbox"));
    }

    @Test
    public void testGetHintsForClusterFormation() {
        List<String> hintList = hints.getHints(ErrorType.CLUSTER_FORMATION, null);

        assertThat(hintList).isNotEmpty();
        assertThat(hintList).anyMatch(hint -> hint.contains("seed node"));
        assertThat(hintList).anyMatch(hint -> hint.contains("network"));
    }

    @Test
    public void testGetHintsForSerializationError() {
        List<String> hintList = hints.getHints(ErrorType.SERIALIZATION_ERROR, null);

        assertThat(hintList).isNotEmpty();
        assertThat(hintList).anyMatch(hint -> hint.contains("JsonSerializable"));
        assertThat(hintList).anyMatch(hint -> hint.contains("Jackson"));
    }

    @Test
    public void testGetHintsWithContextSubstitution() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorClass("OrderActor")
                        .actorPath("/user/order-actor")
                        .build();

        List<String> hintList = hints.getHints(ErrorType.BEAN_NOT_FOUND, context);

        assertThat(hintList).isNotEmpty();
        // Check that {beanClass} was replaced with OrderActor
        assertThat(hintList).anyMatch(hint -> hint.contains("OrderActor"));
    }

    @Test
    public void testGetHintsWithAdditionalInfoSubstitution() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/my-actor")
                        .additionalInfo("currentTimeout", "5s")
                        .build();

        List<String> hintList = hints.getHints(ErrorType.TIMEOUT, context);

        assertThat(hintList).isNotEmpty();
        // Check that {currentTimeout} was replaced with 5s
        assertThat(hintList).anyMatch(hint -> hint.contains("5s"));
        assertThat(hintList).anyMatch(hint -> hint.contains("/user/my-actor"));
    }

    @Test
    public void testGetDocumentationLink() {
        String docLink = hints.getDocumentationLink(ErrorType.BEAN_NOT_FOUND);

        assertThat(docLink).isNotNull();
        assertThat(docLink).contains("https://");
        assertThat(docLink).contains("bean-not-found");
    }

    @Test
    public void testGetDocumentationLinkForTimeout() {
        String docLink = hints.getDocumentationLink(ErrorType.TIMEOUT);

        assertThat(docLink).isNotNull();
        assertThat(docLink).contains("timeout");
    }

    @Test
    public void testGetHintsForUnknownError() {
        List<String> hintList = hints.getHints(ErrorType.UNKNOWN, null);

        assertThat(hintList).isNotEmpty();
        assertThat(hintList).anyMatch(hint -> hint.contains("stack trace"));
    }

    @Test
    public void testGetHintsForAllErrorTypes() {
        // Verify that all error types have hints defined
        for (ErrorType errorType : ErrorType.values()) {
            List<String> hintList = hints.getHints(errorType, null);
            assertThat(hintList)
                    .as("Error type %s should have hints defined", errorType)
                    .isNotEmpty();
        }
    }

    @Test
    public void testGetDocumentationLinksForAllErrorTypes() {
        // Verify that all error types have documentation links
        for (ErrorType errorType : ErrorType.values()) {
            String docLink = hints.getDocumentationLink(errorType);
            assertThat(docLink)
                    .as("Error type %s should have a documentation link", errorType)
                    .isNotNull();
        }
    }

    @Test
    public void testMultipleVariableSubstitution() {
        ErrorContext context =
                ErrorContext.builder()
                        .actorPath("/user/test-actor")
                        .messageType("TestMessage")
                        .additionalInfo("timeout", "10s")
                        .additionalInfo("mailboxSize", "100")
                        .build();

        List<String> hintList = hints.getHints(ErrorType.TIMEOUT, context);

        assertThat(hintList).isNotEmpty();
        // Verify that multiple variables were substituted
        String combinedHints = String.join(" ", hintList);
        assertThat(combinedHints).contains("/user/test-actor");
    }
}
