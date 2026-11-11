package io.github.seonwkim.actor.autoconfigure.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.actor.generation.ActivationEvent;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.spi.GenerationStore;
import io.github.seonwkim.actor.test.InMemoryGenerationStore;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/**
 * The generation endpoint over HTTP: reads by default, activates only when access is unrestricted and the
 * request carries an authenticated user, answers a rejected activation with 409, and audits each attempt.
 * The user comes from a filter that sets the request's principal, as Spring Security would.
 */
class GenerationEndpointTest {

    @SpringBootApplication
    static class App {
        final List<ActivationEvent> events = new CopyOnWriteArrayList<>();

        @Bean
        GenerationStore generationStore() {
            return new InMemoryGenerationStore();
        }

        /** Requests with the header X-User get that user as their principal. */
        @Bean
        Filter user() {
            return (request, response, chain) -> {
                String user = ((HttpServletRequest) request).getHeader("X-User");
                chain.doFilter(
                        user == null
                                ? request
                                : new HttpServletRequestWrapper((HttpServletRequest) request) {
                                    @Override
                                    public Principal getUserPrincipal() {
                                        return () -> user;
                                    }
                                },
                        response);
            };
        }

        @EventListener
        void audit(ActivationEvent event) {
            events.add(event);
        }
    }

    private static ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.actor.shutdown.exit-process=false",
                        "spring.actor.generation.color=blue",
                        "spring.actor.generation.name=orders",
                        "management.endpoints.web.exposure.include=generation")
                .properties(properties)
                .run();
    }

    private record Response(int status, JsonNode body) {}

    private static Response call(ConfigurableApplicationContext context, String method, String body, String user)
            throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/actuator/generation"))
                .header("Content-Type", "application/json")
                .method(
                        method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (user != null) {
            request.header("X-User", user);
        }
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(
                response.statusCode(), response.body().isEmpty() ? null : new ObjectMapper().readTree(response.body()));
    }

    private static String activation(String color, long expectedEpoch, String operationId) {
        return "{\"color\":\"" + color + "\",\"expectedEpoch\":" + expectedEpoch + ",\"operationId\":\"" + operationId
                + "\"}";
    }

    @Test
    void activationGoesThroughOnlyWithUnrestrictedAccessAndAnAuthenticatedUser() throws Exception {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(call(context, "GET", null, null).status()).isEqualTo(200);
            assertThat(call(context, "POST", activation("blue", 0, "deploy-1"), "ops")
                            .status())
                    .as("write access is off by default")
                    .isNotEqualTo(200);
        }
        try (ConfigurableApplicationContext context = start("management.endpoint.generation.access=unrestricted")) {
            App app = context.getBean(App.class);

            Response anonymous = call(context, "POST", activation("blue", 0, "deploy-1"), null);
            Response ops = call(context, "POST", activation("blue", 0, "deploy-1"), "ops");

            assertThat(anonymous.status()).isEqualTo(401);
            assertThat(anonymous.body().get("outcome").asText()).isEqualTo("UNAUTHENTICATED");
            assertThat(ops.status()).isEqualTo(200);
            assertThat(ops.body().at("/generation/epoch").asLong()).isEqualTo(1);
            assertThat(app.events)
                    .extracting(event -> event.result().outcome(), ActivationEvent::principal)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(Outcome.UNAUTHENTICATED, null),
                            org.assertj.core.groups.Tuple.tuple(Outcome.ACTIVATED, "ops"));
        }
    }

    @Test
    void aRetryAnswersLikeTheFirstAttemptAndACompetingDeployIsAConflict() throws Exception {
        try (ConfigurableApplicationContext context = start("management.endpoint.generation.access=unrestricted")) {
            Response first = call(context, "POST", activation("green", 0, "deploy-1"), "ops");
            Response retry = call(context, "POST", activation("green", 0, "deploy-1"), "ops");
            Response competing = call(context, "POST", activation("blue", 0, "deploy-2"), "ops");

            assertThat(retry.status()).isEqualTo(200);
            assertThat(retry.body().get("generation")).isEqualTo(first.body().get("generation"));
            assertThat(competing.status()).isEqualTo(409);
            assertThat(competing.body().get("outcome").asText()).isEqualTo("EPOCH_MISMATCH");
            JsonNode read = call(context, "GET", null, null).body();
            assertThat(read.at("/active/color").asText()).isEqualTo("green");
            assertThat(read.get("color").asText()).isEqualTo("blue");
            assertThat(context.getBean(MeterRegistry.class)
                            .get(GenerationConfiguration.METER)
                            .tag("outcome", "EPOCH_MISMATCH")
                            .counter()
                            .count())
                    .isEqualTo(1);
        }
    }
}
