package io.github.seonwkim.actor.metrics.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;

/**
 * Checks the Spring Boot configuration metadata that the configuration processor generates for
 * the metrics module.
 */
class ActorMetricsConfigurationMetadataTest {

    private static final String METADATA = "META-INF/spring-configuration-metadata.json";

    @Test
    void metricsEnabledKeyIsDescribedWithDefaultTrue() throws Exception {
        JsonNode property = property(readOwnMetadata(), "spring.actor.metrics.enabled");

        assertNotNull(property, "spring.actor.metrics.enabled must be in the metadata");
        assertEquals("java.lang.Boolean", property.path("type").asText());
        assertTrue(property.path("defaultValue").isBoolean(), "default must be a boolean");
        assertTrue(property.path("defaultValue").asBoolean(), "default must be true");
        assertFalse(property.path("description").asText().trim().isEmpty(), "description must not be empty");
    }

    private static JsonNode property(JsonNode metadata, String name) {
        for (JsonNode property : metadata.path("properties")) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        return null;
    }

    /** Reads the metadata file that belongs to the metrics module's own classes. */
    private static JsonNode readOwnMetadata() throws Exception {
        String ownLocation = ActorMetricsProperties.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
        Enumeration<URL> resources =
                ActorMetricsConfigurationMetadataTest.class.getClassLoader().getResources(METADATA);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (url.getPath().contains(ownLocation)) {
                try (InputStream in = url.openStream()) {
                    return new ObjectMapper().readTree(in);
                }
            }
        }
        throw new AssertionError(METADATA + " not found next to metrics classes at " + ownLocation);
    }
}
