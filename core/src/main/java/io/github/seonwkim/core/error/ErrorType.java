package io.github.seonwkim.core.error;

/**
 * Categorizes different types of errors that can occur in the Spring Actor System.
 * Each error type has associated troubleshooting hints and documentation links.
 */
public enum ErrorType {
    /** Bean not found or dependency injection errors */
    BEAN_NOT_FOUND("bean-not-found"),

    /** Actor spawn or creation failures */
    ACTOR_SPAWN_FAILURE("actor-spawn-failure"),

    /** Ask timeout errors */
    TIMEOUT("timeout"),

    /** Dead letter errors - message sent to stopped actor */
    DEAD_LETTER("dead-letter"),

    /** Unhandled message type */
    UNHANDLED_MESSAGE("unhandled-message"),

    /** Serialization errors in cluster mode */
    SERIALIZATION_ERROR("serialization-error"),

    /** Cluster formation failures */
    CLUSTER_FORMATION("cluster-formation"),

    /** Node unreachable in cluster */
    CLUSTER_UNREACHABLE("cluster-unreachable"),

    /** Split brain scenario */
    CLUSTER_SPLIT_BRAIN("cluster-split-brain"),

    /** Sharding region not started */
    SHARDING_NOT_STARTED("sharding-not-started"),

    /** Invalid configuration errors */
    CONFIGURATION_ERROR("configuration-error"),

    /** Invalid actor reference format */
    INVALID_ACTOR_REF("invalid-actor-ref"),

    /** Restart loop - actor failing repeatedly */
    RESTART_LOOP("restart-loop"),

    /** Supervision strategy issues */
    SUPERVISION_ERROR("supervision-error"),

    /** Generic or unknown error */
    UNKNOWN("unknown");

    private final String key;

    ErrorType(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
