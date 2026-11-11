package io.github.seonwkim.actor.autoconfigure.health;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.health.AdditionalHealthEndpointPath;
import org.springframework.boot.actuate.health.HealthEndpointGroup;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.health.HealthEndpointGroupsPostProcessor;
import org.springframework.boot.actuate.health.HttpCodeStatusMapper;
import org.springframework.boot.actuate.health.StatusAggregator;

/**
 * Adds the {@code warmup} health group ({@code /actuator/health/warmup}) with the {@code actorWarmup}
 * component, the way Spring Boot adds its {@code liveness} and {@code readiness} groups. A group named
 * {@code warmup} in {@code management.endpoint.health.group} replaces it. It shows components and
 * details as the main health endpoint does.
 */
class WarmupHealthGroup implements HealthEndpointGroupsPostProcessor {

    static final String NAME = "warmup";
    static final String COMPONENT = "actorWarmup";

    @Override
    public HealthEndpointGroups postProcessHealthEndpointGroups(HealthEndpointGroups groups) {
        if (groups.get(NAME) != null) {
            return groups;
        }
        Map<String, HealthEndpointGroup> named = new LinkedHashMap<>();
        groups.getNames().forEach(name -> named.put(name, groups.get(name)));
        named.put(NAME, new Group(groups.getPrimary()));
        return HealthEndpointGroups.of(groups.getPrimary(), named);
    }

    private record Group(HealthEndpointGroup primary) implements HealthEndpointGroup {

        @Override
        public boolean isMember(String name) {
            return COMPONENT.equals(name);
        }

        @Override
        public boolean showComponents(SecurityContext securityContext) {
            return primary.showComponents(securityContext);
        }

        @Override
        public boolean showDetails(SecurityContext securityContext) {
            return primary.showDetails(securityContext);
        }

        @Override
        public StatusAggregator getStatusAggregator() {
            return primary.getStatusAggregator();
        }

        @Override
        public HttpCodeStatusMapper getHttpCodeStatusMapper() {
            return primary.getHttpCodeStatusMapper();
        }

        @Override
        @Nullable public AdditionalHealthEndpointPath getAdditionalPath() {
            return null;
        }
    }
}
