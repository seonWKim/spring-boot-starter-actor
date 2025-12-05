package io.github.seonwkim.core.error;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Provides contextual information about an error for enhanced error messages.
 * This context is used to generate more specific and actionable troubleshooting hints.
 */
public class ErrorContext {
    @Nullable private final String actorClass;
    @Nullable private final String actorId;
    @Nullable private final String actorPath;
    @Nullable private final String messageType;
    @Nullable private final String actorState;
    private final Map<String, Object> additionalInfo;

    private ErrorContext(Builder builder) {
        this.actorClass = builder.actorClass;
        this.actorId = builder.actorId;
        this.actorPath = builder.actorPath;
        this.messageType = builder.messageType;
        this.actorState = builder.actorState;
        this.additionalInfo = new HashMap<>(builder.additionalInfo);
    }

    @Nullable
    public String getActorClass() {
        return actorClass;
    }

    @Nullable
    public String getActorId() {
        return actorId;
    }

    @Nullable
    public String getActorPath() {
        return actorPath;
    }

    @Nullable
    public String getMessageType() {
        return messageType;
    }

    @Nullable
    public String getActorState() {
        return actorState;
    }

    public Map<String, Object> getAdditionalInfo() {
        return new HashMap<>(additionalInfo);
    }

    @Nullable
    public Object getAdditionalInfo(String key) {
        return additionalInfo.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        @Nullable private String actorClass;
        @Nullable private String actorId;
        @Nullable private String actorPath;
        @Nullable private String messageType;
        @Nullable private String actorState;
        private final Map<String, Object> additionalInfo = new HashMap<>();

        public Builder actorClass(String actorClass) {
            this.actorClass = actorClass;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder actorPath(String actorPath) {
            this.actorPath = actorPath;
            return this;
        }

        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder actorState(String actorState) {
            this.actorState = actorState;
            return this;
        }

        public Builder additionalInfo(String key, Object value) {
            this.additionalInfo.put(key, value);
            return this;
        }

        public ErrorContext build() {
            return new ErrorContext(this);
        }
    }
}
