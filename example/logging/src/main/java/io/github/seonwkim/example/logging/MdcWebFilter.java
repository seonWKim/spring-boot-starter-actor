package io.github.seonwkim.example.logging;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that sets up MDC context for each incoming HTTP request.
 * This ensures that all logs during a request have consistent request-level metadata.
 *
 * Sets the following MDC values:
 * - requestId: A unique UUID for each request (for request tracing)
 * - userId: Extracted from request header "X-User-Id" if present
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcWebFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Generate or extract request ID
        String requestId = exchange.getRequest()
                .getHeaders()
                .getFirst(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        // Extract user ID if present
        String userId = exchange.getRequest()
                .getHeaders()
                .getFirst(USER_ID_HEADER);

        // Set MDC values
        MDC.put(REQUEST_ID_KEY, requestId);
        if (userId != null && !userId.isEmpty()) {
            MDC.put(USER_ID_KEY, userId);
        }

        // Also add request ID to response headers for debugging
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

        // Continue the filter chain and ensure MDC is cleared after request completes
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    MDC.remove(REQUEST_ID_KEY);
                    MDC.remove(USER_ID_KEY);
                });
    }
}
