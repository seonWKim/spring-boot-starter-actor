package io.github.seonwkim.core.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.beans.PropertyDescriptor;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Checks the Spring Boot configuration metadata that the configuration processor generates for
 * core: every key bound by a {@code @ConfigurationProperties} class in core must be listed with a
 * description, and the {@code spring.actor.*} Pekko pass-through block must be documented.
 */
class ConfigurationMetadataTest {

    private static final String METADATA = "META-INF/spring-configuration-metadata.json";
    private static final String CORE_PACKAGE = "io.github.seonwkim.core";

    private static JsonNode metadata;

    @BeforeAll
    static void loadMetadata() throws Exception {
        metadata = readOwnMetadata();
    }

    @Test
    void managementEnabledKeyIsDescribedWithDefaultTrue() {
        JsonNode property = property("spring.actor.pekko.management.enabled");

        assertThat(property).as("spring.actor.pekko.management.enabled in metadata").isNotNull();
        assertThat(property.path("type").asText()).isEqualTo("java.lang.Boolean");
        assertThat(property.path("defaultValue").isBoolean()).isTrue();
        assertThat(property.path("defaultValue").asBoolean()).isTrue();
        assertThat(property.path("description").asText()).isNotBlank();
    }

    @Test
    void everyKeyBoundInCoreIsListedWithDescription() throws Exception {
        List<String> boundKeys = boundKeysInCore();

        assertThat(boundKeys).contains("spring.actor.pekko.management.enabled");
        for (String key : boundKeys) {
            JsonNode property = property(key);
            assertThat(property).as("metadata entry for " + key).isNotNull();
            assertThat(property.path("description").asText())
                    .as("description of " + key)
                    .isNotBlank();
        }
    }

    @Test
    void everyListedPropertyHasDescription() {
        for (JsonNode property : metadata.path("properties")) {
            assertThat(property.path("description").asText())
                    .as("description of " + property.path("name").asText())
                    .isNotBlank();
        }
    }

    @Test
    void pekkoPassThroughBlockIsDocumented() {
        JsonNode group = null;
        for (JsonNode candidate : metadata.path("groups")) {
            if ("spring.actor".equals(candidate.path("name").asText())) {
                group = candidate;
            }
        }

        assertThat(group).as("spring.actor group in metadata").isNotNull();
        assertThat(group.path("description").asText()).isNotBlank();
    }

    private static JsonNode property(String name) {
        for (JsonNode property : metadata.path("properties")) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        return null;
    }

    /** Reads the metadata file that belongs to core's own classes, not to a Spring Boot jar. */
    private static JsonNode readOwnMetadata() throws Exception {
        String ownLocation = PekkoManagementProperties.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
        Enumeration<URL> resources =
                ConfigurationMetadataTest.class.getClassLoader().getResources(METADATA);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (url.getPath().contains(ownLocation)) {
                try (InputStream in = url.openStream()) {
                    return new ObjectMapper().readTree(in);
                }
            }
        }
        throw new AssertionError(METADATA + " not found next to core classes at " + ownLocation);
    }

    /** Derives the keys bound by every {@code @ConfigurationProperties} class in core. */
    private static List<String> boundKeysInCore() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));

        Map<String, Class<?>> classes = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(CORE_PACKAGE)) {
            String className = candidate.getBeanClassName();
            if (className != null) {
                classes.put(className, ClassUtils.forName(className, null));
            }
        }

        List<String> keys = new ArrayList<>();
        for (Class<?> type : classes.values()) {
            // Only main classes count; test fixtures are not part of the published metadata.
            if (!type.getProtectionDomain()
                    .getCodeSource()
                    .equals(PekkoManagementProperties.class.getProtectionDomain().getCodeSource())) {
                continue;
            }
            ConfigurationProperties annotation = type.getAnnotation(ConfigurationProperties.class);
            String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
            for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(type)) {
                if (descriptor.getWriteMethod() != null) {
                    keys.add(prefix + "." + toKebabCase(descriptor.getName()));
                }
            }
        }
        return keys;
    }

    private static String toKebabCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
