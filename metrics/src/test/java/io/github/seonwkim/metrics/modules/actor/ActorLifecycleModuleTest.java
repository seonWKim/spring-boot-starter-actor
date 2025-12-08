package io.github.seonwkim.metrics.modules.actor;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for ActorLifecycleModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class ActorLifecycleModuleTest {

    private TestMetricsBackend meterRegistry;
    private MetricsRegistry metricsRegistry;
    private ActorLifecycleModule module;

    @BeforeEach
    void setUp() {
        // Create test meter registry
        meterRegistry = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "actor-lifecycle")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .meterRegistry(meterRegistry)
                .build();

        // Create and register module
        module = new ActorLifecycleModule();
        metricsRegistry.registerModule(module);
    }

    @AfterEach
    void tearDown() {
        if (module != null) {
            module.shutdown();
        }
    }

    @Test
    void testModuleMetadata() {
        assertEquals("actor-lifecycle", module.moduleId());
    }

    @Test
    void testModuleInitialization() {
        // Verify gauge was created during initialization
        // Note: Counters for created/terminated are created per-actor with specific tags,
        // not during module initialization
        Gauge activeGauge = meterRegistry.find("actor.lifecycle.active").gauge();
        assertNotNull(activeGauge, "Active gauge should be created during initialization");
    }

    @Test
    void testModuleShutdown() {
        // Module should shutdown cleanly
        assertDoesNotThrow(() -> module.shutdown());

        // Calling shutdown twice should be safe
        assertDoesNotThrow(() -> module.shutdown());
    }

    @Test
    void testMetricsBackendIntegration() {
        // Verify that metrics backend is properly used for the active gauge
        // Note: created/terminated counters are created dynamically when actors are spawned
        Gauge activeGauge = meterRegistry.find("actor.lifecycle.active").gauge();
        assertNotNull(activeGauge);

        // Initial active count should be 0
        assertEquals(0.0, activeGauge.value());
    }

    @Test
    void testConfigurationIntegration() {
        // Verify registry configuration is accessible
        MetricsConfiguration config = metricsRegistry.getConfiguration();
        assertNotNull(config);
        assertTrue(config.isEnabled());

        // Verify global tags are applied
        assertNotNull(metricsRegistry.getGlobalTags());
    }

    @Test
    void testModuleRegistration() {
        // Verify module is in registry's module list
        var modules = metricsRegistry.getModules();
        assertTrue(modules.contains(module));
        assertEquals(1, modules.size());
    }
}
