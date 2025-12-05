package io.github.seonwkim.core.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Fluent builder for creating enhanced error messages with troubleshooting hints.
 * Provides both compact and verbose formatting options.
 */
public class ErrorMessageBuilder {
    private final Throwable error;
    private final List<String> hints = new ArrayList<>();
    @Nullable private String documentationLink;
    @Nullable private ErrorContext context;
    private boolean includeStackTrace = true;
    private boolean verboseMode = false;

    public ErrorMessageBuilder(Throwable error) {
        this.error = error;
    }

    public ErrorMessageBuilder addHints(List<String> hints) {
        this.hints.addAll(hints);
        return this;
    }

    public ErrorMessageBuilder addDocumentationLink(@Nullable String link) {
        this.documentationLink = link;
        return this;
    }

    public ErrorMessageBuilder withContext(@Nullable ErrorContext context) {
        this.context = context;
        return this;
    }

    public ErrorMessageBuilder includeStackTrace(boolean include) {
        this.includeStackTrace = include;
        return this;
    }

    public ErrorMessageBuilder verboseMode(boolean verbose) {
        this.verboseMode = verbose;
        return this;
    }

    /**
     * Builds the formatted error message.
     *
     * @return The formatted error message string
     */
    public String build() {
        if (verboseMode) {
            return buildVerboseMessage();
        } else {
            return buildCompactMessage();
        }
    }

    private String buildCompactMessage() {
        StringBuilder sb = new StringBuilder();

        // Error message
        String errorMsg = error.getMessage() != null ? error.getMessage() : "No message";
        sb.append(error.getClass().getSimpleName()).append(": ").append(errorMsg);

        // First hint only in compact mode
        if (!hints.isEmpty()) {
            sb.append("\n💡 Hint: ").append(hints.get(0));
        }

        // Documentation link
        if (documentationLink != null) {
            sb.append("\n📖 Docs: ").append(documentationLink);
        }

        // Actor context (brief)
        if (context != null) {
            appendCompactContext(sb);
        }

        return sb.toString();
    }

    private String buildVerboseMessage() {
        StringBuilder sb = new StringBuilder();

        // Box header
        sb.append("╭─ ")
                .append(error.getClass().getSimpleName())
                .append(" ")
                .append("─".repeat(Math.max(0, 60 - error.getClass().getSimpleName().length())))
                .append("╮\n");

        // Error message
        String errorMsg = error.getMessage() != null ? error.getMessage() : "No message";
        sb.append("│ ").append(padRight(errorMsg, 68)).append(" │\n");

        // Cause if present
        if (error.getCause() != null) {
            String causeMsg =
                    error.getCause().getMessage() != null
                            ? error.getCause().getMessage()
                            : "No message";
            sb.append("│ Cause: ").append(padRight(causeMsg, 61)).append(" │\n");
        }

        sb.append("│").append(" ".repeat(70)).append("│\n");

        // Troubleshooting hints
        if (!hints.isEmpty()) {
            sb.append("│ 💡 Troubleshooting hints:").append(" ".repeat(43)).append("│\n");
            for (int i = 0; i < hints.size(); i++) {
                String hint = hints.get(i);
                sb.append("│   ")
                        .append(i + 1)
                        .append(". ")
                        .append(padRight(hint, 64))
                        .append(" │\n");
            }
            sb.append("│").append(" ".repeat(70)).append("│\n");
        }

        // Documentation link
        if (documentationLink != null) {
            sb.append("│ 📖 Documentation:").append(" ".repeat(51)).append("│\n");
            sb.append("│   ").append(padRight(documentationLink, 66)).append(" │\n");
            sb.append("│").append(" ".repeat(70)).append("│\n");
        }

        // Actor context
        if (context != null) {
            appendVerboseContext(sb);
        }

        // Stack trace indicator
        if (includeStackTrace) {
            sb.append("│ 🔍 Stack trace: [See below]")
                    .append(" ".repeat(41))
                    .append("│\n");
        }

        // Box footer
        sb.append("╰").append("─".repeat(70)).append("╯\n");

        // Actual stack trace
        if (includeStackTrace) {
            sb.append("\n");
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        }

        return sb.toString();
    }

    private void appendCompactContext(StringBuilder sb) {
        if (context == null) {
            return;
        }
        if (context.getActorPath() != null) {
            sb.append("\n🎯 Actor: ").append(context.getActorPath());
        }
        if (context.getMessageType() != null) {
            sb.append("\n📨 Message: ").append(context.getMessageType());
        }
    }

    private void appendVerboseContext(StringBuilder sb) {
        if (context == null) {
            return;
        }
        sb.append("│ 🎯 Actor Context:").append(" ".repeat(51)).append("│\n");

        if (context.getActorPath() != null) {
            sb.append("│   - Actor Path: ")
                    .append(padRight(context.getActorPath(), 52))
                    .append(" │\n");
        }

        if (context.getMessageType() != null) {
            sb.append("│   - Message Type: ")
                    .append(padRight(context.getMessageType(), 50))
                    .append(" │\n");
        }

        if (context.getActorState() != null) {
            sb.append("│   - Actor State: ")
                    .append(padRight(context.getActorState(), 51))
                    .append(" │\n");
        }

        if (context.getActorClass() != null) {
            sb.append("│   - Actor Class: ")
                    .append(padRight(context.getActorClass(), 51))
                    .append(" │\n");
        }

        sb.append("│").append(" ".repeat(70)).append("│\n");
    }

    private String padRight(String text, int length) {
        String safeText = (text != null) ? text : "";
        if (safeText.length() > length) {
            return safeText.substring(0, length - 3) + "...";
        }
        return safeText + " ".repeat(Math.max(0, length - safeText.length()));
    }
}
