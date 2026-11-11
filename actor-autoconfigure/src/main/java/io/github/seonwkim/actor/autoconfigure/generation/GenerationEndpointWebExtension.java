package io.github.seonwkim.actor.autoconfigure.generation;

import io.github.seonwkim.actor.generation.ActivationResult;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;

/**
 * The {@code generation} endpoint over HTTP: a rejected activation fails loudly, with {@code 409 Conflict}
 * for a mismatched epoch or a reused operation ID, and {@code 401 Unauthorized} without an authenticated
 * user. The body is the {@link ActivationResult} either way.
 */
@EndpointWebExtension(endpoint = GenerationEndpoint.class)
public class GenerationEndpointWebExtension {

    private final GenerationEndpoint endpoint;

    public GenerationEndpointWebExtension(GenerationEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @WriteOperation
    public WebEndpointResponse<ActivationResult> activate(
            String color, long expectedEpoch, String operationId, SecurityContext securityContext) {
        ActivationResult result = endpoint.activate(color, expectedEpoch, operationId, securityContext);
        int status =
                switch (result.outcome()) {
                    case ACTIVATED, ALREADY_ACTIVATED -> WebEndpointResponse.STATUS_OK;
                    case EPOCH_MISMATCH, OPERATION_CONFLICT -> 409;
                    case UNAUTHENTICATED -> 401;
                };
        return new WebEndpointResponse<>(result, status);
    }
}
