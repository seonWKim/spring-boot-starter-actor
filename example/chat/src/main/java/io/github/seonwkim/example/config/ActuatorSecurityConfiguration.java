package io.github.seonwkim.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Activating a blue-green generation ({@code POST /actuator/generation}) needs a user with the
 * {@code DEPLOYER} role, over HTTP basic auth; everything else stays open, as in the rest of this
 * example. The user comes from {@code spring.security.user.*} (in Kubernetes, from a Secret).
 */
@Configuration(proxyBeanMethods = false)
public class ActuatorSecurityConfiguration {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(exchanges -> exchanges
                        .matchers(ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, "/actuator/generation"))
                        .hasRole("DEPLOYER")
                        .anyExchange()
                        .permitAll())
                .httpBasic(Customizer.withDefaults())
                // A pipeline calls the endpoint, not a browser session: no CSRF token to send.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }
}
