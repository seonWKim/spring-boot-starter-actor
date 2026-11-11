package io.github.seonwkim.actor.generation;

import org.springframework.context.ApplicationEvent;
import org.springframework.lang.Nullable;

/**
 * Published for every activation attempt, accepted or rejected, for audit. The starter also logs each
 * attempt and counts it in the {@code actor.generation.activations} meter (tag {@code outcome}).
 */
public class ActivationEvent extends ApplicationEvent {

    private final String deployment;
    private final String color;
    private final long expectedEpoch;
    private final String operationId;

    @Nullable private final String principal;

    private final ActivationResult result;

    public ActivationEvent(
            Object source,
            String deployment,
            String color,
            long expectedEpoch,
            String operationId,
            @Nullable String principal,
            ActivationResult result) {
        super(source);
        this.deployment = deployment;
        this.color = color;
        this.expectedEpoch = expectedEpoch;
        this.operationId = operationId;
        this.principal = principal;
        this.result = result;
    }

    /** The deployment ({@code spring.actor.generation.name}) whose generation was to change. */
    public String deployment() {
        return deployment;
    }

    /** The color the attempt asked for. */
    public String color() {
        return color;
    }

    /** The epoch the attempt expected to be active. */
    public long expectedEpoch() {
        return expectedEpoch;
    }

    /** The attempt's deployment operation ID. */
    public String operationId() {
        return operationId;
    }

    /** The authenticated user's name for an attempt through the endpoint; null from application code. */
    @Nullable public String principal() {
        return principal;
    }

    /** What the attempt did. */
    public ActivationResult result() {
        return result;
    }
}
