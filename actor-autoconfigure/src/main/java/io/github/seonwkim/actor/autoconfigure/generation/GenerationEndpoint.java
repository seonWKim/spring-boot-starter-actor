package io.github.seonwkim.actor.autoconfigure.generation;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import java.security.Principal;
import javax.annotation.Nullable;
import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * The {@code generation} Actuator endpoint: reads the active generation, and activates a color with a
 * compare-and-set on the epoch. Reading is allowed by default; activating needs
 * {@code management.endpoint.generation.access=unrestricted} and an authenticated user (secure the
 * endpoint with Spring Security). An attempt without one is rejected with {@code UNAUTHENTICATED} and
 * audited like any other.
 */
@Endpoint(id = "generation", defaultAccess = Access.READ_ONLY)
public class GenerationEndpoint {

    /**
     * What a read returns.
     *
     * @param deployment the deployment name
     * @param color this node's color
     * @param active the active generation, null before the first activation
     */
    public record Generation(String deployment, String color, @Nullable Active active) {}

    /**
     * A generation as JSON. {@code activatedAt} is an ISO-8601 string: Actuator's own JSON mapper may not
     * know {@code java.time} types.
     */
    public record Active(String color, long epoch, String operationId, String activatedAt) {

        @Nullable static Active of(@Nullable ActiveGeneration generation) {
            return generation == null
                    ? null
                    : new Active(
                            generation.color(),
                            generation.epoch(),
                            generation.operationId(),
                            generation.activatedAt().toString());
        }
    }

    /** What an activation returns: the outcome and the generation it refers to (see {@link ActivationResult}). */
    public record Activation(ActivationResult.Outcome outcome, @Nullable Active generation) {

        static Activation of(ActivationResult result) {
            return new Activation(result.outcome(), Active.of(result.generation()));
        }
    }

    private final StoreDeploymentGeneration generation;

    public GenerationEndpoint(StoreDeploymentGeneration generation) {
        this.generation = generation;
    }

    /** Reads the store, not the reused read. */
    @ReadOperation
    public Generation read() {
        return new Generation(
                generation.deployment(),
                generation.color(),
                Active.of(generation.activeNow().orElse(null)));
    }

    @WriteOperation
    public Activation activate(String color, long expectedEpoch, String operationId, SecurityContext securityContext) {
        Principal principal = securityContext.getPrincipal();
        if (principal == null) {
            ActivationResult rejected = new ActivationResult(
                    ActivationResult.Outcome.UNAUTHENTICATED,
                    generation.activeNow().orElse(null));
            generation.audit(color, expectedEpoch, operationId, null, rejected);
            return Activation.of(rejected);
        }
        return Activation.of(generation.activate(color, expectedEpoch, operationId, principal.getName()));
    }
}
