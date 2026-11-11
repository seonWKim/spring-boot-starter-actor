package io.github.seonwkim.actor.generation;

import org.springframework.lang.Nullable;

/**
 * What an activation attempt did.
 *
 * @param outcome whether it activated, was a retry of one that did, or was rejected and why
 * @param generation the generation the operation activated ({@code ACTIVATED}, {@code ALREADY_ACTIVATED}),
 *     the one the operation ID already activated ({@code OPERATION_CONFLICT}), or the active one
 *     ({@code EPOCH_MISMATCH}, {@code UNAUTHENTICATED}); null when none was ever activated
 */
public record ActivationResult(Outcome outcome, @Nullable ActiveGeneration generation) {

    /** Whether the requested color is active by this operation: true for a first attempt and for a retry. */
    public boolean accepted() {
        return outcome == Outcome.ACTIVATED || outcome == Outcome.ALREADY_ACTIVATED;
    }

    public enum Outcome {
        /** The color is active at the expected epoch plus one. */
        ACTIVATED,
        /** A retry: this operation ID activated the same color from the same epoch before; nothing changed. */
        ALREADY_ACTIVATED,
        /** The active epoch is not the expected one: another deployment activated in between. */
        EPOCH_MISMATCH,
        /** This operation ID already activated a different color or from a different epoch. */
        OPERATION_CONFLICT,
        /** The request carried no authenticated user. */
        UNAUTHENTICATED
    }
}
