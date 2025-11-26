package io.github.seonwkim.metrics.filter;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.core.MetricsConfiguration.FilterConfig;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Filter engine for determining which actors should be instrumented.
 * Supports glob patterns for actor paths and message types.
 */
public class FilterEngine {

    private final List<Pattern> actorIncludePatterns;
    private final List<Pattern> actorExcludePatterns;
    private final List<Pattern> messageIncludePatterns;
    private final List<Pattern> messageExcludePatterns;

    private FilterEngine(Builder builder) {
        this.actorIncludePatterns = builder.actorIncludePatterns;
        this.actorExcludePatterns = builder.actorExcludePatterns;
        this.messageIncludePatterns = builder.messageIncludePatterns;
        this.messageExcludePatterns = builder.messageExcludePatterns;
    }

    /**
     * Create FilterEngine from configuration.
     */
    public static FilterEngine from(FilterConfig config) {
        return builder()
                .includeActors(config.getActorInclude())
                .excludeActors(config.getActorExclude())
                .includeMessages(config.getMessageInclude())
                .excludeMessages(config.getMessageExclude())
                .build();
    }

    /**
     * Create a new builder for FilterEngine.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Check if an actor matches the filter criteria.
     * @param context the actor context
     * @return true if the actor should be instrumented
     */
    public boolean matches(ActorContext context) {
        String actorPath = context.getPath();

        // If no include patterns specified, include all by default
        boolean included = actorIncludePatterns.isEmpty()
                || actorIncludePatterns.stream()
                        .anyMatch(p -> p.matcher(actorPath).matches());

        // Check exclude patterns
        boolean excluded =
                actorExcludePatterns.stream().anyMatch(p -> p.matcher(actorPath).matches());

        return included && !excluded;
    }

    /**
     * Check if a message type matches the filter criteria.
     * @param messageClass the message class name
     * @return true if the message should be instrumented
     */
    public boolean matchesMessage(String messageClass) {
        // If no include patterns specified, include all by default
        boolean included = messageIncludePatterns.isEmpty()
                || messageIncludePatterns.stream()
                        .anyMatch(p -> p.matcher(messageClass).matches());

        // Check exclude patterns
        boolean excluded = messageExcludePatterns.stream()
                .anyMatch(p -> p.matcher(messageClass).matches());

        return included && !excluded;
    }

    /**
     * Convert glob pattern to regex pattern.
     * Supports: * (any chars), ** (any path segments), ? (single char)
     */
    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                // Check for **
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i += 2;
                } else {
                    regex.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                regex.append("[^/]");
                i++;
            } else if ("[]{}().+^$|\\".indexOf(c) != -1) {
                // Escape regex special characters
                regex.append('\\').append(c);
                i++;
            } else {
                regex.append(c);
                i++;
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    /**
     * Builder for FilterEngine.
     */
    public static class Builder {
        private List<Pattern> actorIncludePatterns = List.of();
        private List<Pattern> actorExcludePatterns = List.of();
        private List<Pattern> messageIncludePatterns = List.of();
        private List<Pattern> messageExcludePatterns = List.of();

        public Builder includeActors(List<String> patterns) {
            this.actorIncludePatterns =
                    patterns.stream().map(FilterEngine::globToPattern).collect(Collectors.toList());
            return this;
        }

        public Builder excludeActors(List<String> patterns) {
            this.actorExcludePatterns =
                    patterns.stream().map(FilterEngine::globToPattern).collect(Collectors.toList());
            return this;
        }

        public Builder includeMessages(List<String> patterns) {
            this.messageIncludePatterns =
                    patterns.stream().map(FilterEngine::globToPattern).collect(Collectors.toList());
            return this;
        }

        public Builder excludeMessages(List<String> patterns) {
            this.messageExcludePatterns =
                    patterns.stream().map(FilterEngine::globToPattern).collect(Collectors.toList());
            return this;
        }

        public FilterEngine build() {
            return new FilterEngine(this);
        }
    }
}
