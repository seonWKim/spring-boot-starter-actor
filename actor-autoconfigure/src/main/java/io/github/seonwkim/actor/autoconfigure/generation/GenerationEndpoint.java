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
    public record Generation(String deployment, String color, @Nullable ActiveGeneration active) {}

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
                generation.activeNow().orElse(null));
    }

    @WriteOperation
    public ActivationResult activate(
            String color, long expectedEpoch, String operationId, SecurityContext securityContext) {
        Principal principal = securityContext.getPrincipal();
        if (principal == null) {
            ActivationResult rejected = new ActivationResult(
                    ActivationResult.Outcome.UNAUTHENTICATED,
                    generation.activeNow().orElse(null));
            generation.audit(color, expectedEpoch, operationId, null, rejected);
            return rejected;
        }
        return generation.activate(color, expectedEpoch, operationId, principal.getName());
    }
}
