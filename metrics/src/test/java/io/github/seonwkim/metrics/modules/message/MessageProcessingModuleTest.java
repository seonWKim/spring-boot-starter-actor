package io.github.seonwkim.metrics.modules.message;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for MessageProcessingModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class MessageProcessingModuleTest {

    private TestMetricsBackend meterRegistry;
    private MetricsRegistry metricsRegistry;
    private MessageProcessingModule module;

    @BeforeEach
    void setUp() {
        // Create test meter registry
        meterRegistry = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "message-processing")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .meterRegistry(meterRegistry)
                .build();

        // Create and register module
        module = new MessageProcessingModule();
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
        assertEquals("message-processing", module.moduleId());
    }

    @Test
    void testModuleInitialization() {
        // Module should initialize without errors
        assertNotNull(module);
    }

    @Test
    void testModuleShutdown() {
        // Module should shutdown cleanly
        assertDoesNotThrow(() -> module.shutdown());

        // Calling shutdown twice should be safe
        assertDoesNotThrow(() -> module.shutdown());
    }

    @Test
    void testMetricsCreation() {
        // Simulate creating metrics that would be used during instrumentation
        List<Tag> testTags = new ArrayList<>();
        testTags.add(Tag.of("actor.class", "TestActor"));
        testTags.add(Tag.of("message.type", "TestMessage"));
        metricsRegistry.getGlobalTags().forEach(testTags::add);

        // Processing time timer
        Timer processingTimer =
                Timer.builder("actor.message.processing.time").tags(testTags).register(meterRegistry);
        assertNotNull(processingTimer);

        // Processed counter
        Counter processedCounter =
                Counter.builder("actor.message.processed").tags(testTags).register(meterRegistry);
        assertNotNull(processedCounter);

        // Error counter
        List<Tag> errorTags = new ArrayList<>(testTags);
        errorTags.add(Tag.of("error.type", "NullPointerException"));
        Counter errorCounter =
                Counter.builder("actor.message.errors").tags(errorTags).register(meterRegistry);
        assertNotNull(errorCounter);

        // Verify metrics work
        processingTimer.record(100, TimeUnit.MILLISECONDS);
        assertEquals(1, processingTimer.count());

        processedCounter.increment();
        assertEquals(1.0, processedCounter.count());

        errorCounter.increment();
        assertEquals(1.0, errorCounter.count());
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

    @Test
    void testMultipleMessageTypes() {
        // Simulate tracking different message types
        String[] messageTypes = {"SayHello", "Stop", "GetStatus", "UpdateState"};

        for (String messageType : messageTypes) {
            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of("actor.class", "TestActor"));
            tags.add(Tag.of("message.type", messageType));
            metricsRegistry.getGlobalTags().forEach(tags::add);

            Timer timer =
                    Timer.builder("actor.message.processing.time").tags(tags).register(meterRegistry);
            timer.record(50, TimeUnit.MILLISECONDS);

            Counter counter =
                    Counter.builder("actor.message.processed").tags(tags).register(meterRegistry);
            counter.increment();
        }

        // Verify we have metrics for all message types
        assertEquals(
                4,
                meterRegistry.getMeters().stream()
                        .filter(m -> m instanceof Timer)
                        .count());
        assertEquals(
                4,
                meterRegistry.getMeters().stream()
                        .filter(m -> m instanceof Counter && m.getId().getName().equals("actor.message.processed"))
                        .count());
    }

    @Test
    void testErrorTracking() {
        List<Tag> baseTags = new ArrayList<>();
        baseTags.add(Tag.of("actor.class", "FailingActor"));
        baseTags.add(Tag.of("message.type", "FailingMessage"));
        metricsRegistry.getGlobalTags().forEach(baseTags::add);

        // Simulate successful processing
        Counter processedCounter =
                Counter.builder("actor.message.processed").tags(baseTags).register(meterRegistry);
        processedCounter.increment();
        processedCounter.increment();

        // Simulate errors
        List<Tag> error1Tags = new ArrayList<>(baseTags);
        error1Tags.add(Tag.of("error.type", "NullPointerException"));
        Counter errorCounter1 =
                Counter.builder("actor.message.errors").tags(error1Tags).register(meterRegistry);
        errorCounter1.increment();

        List<Tag> error2Tags = new ArrayList<>(baseTags);
        error2Tags.add(Tag.of("error.type", "IllegalArgumentException"));
        Counter errorCounter2 =
                Counter.builder("actor.message.errors").tags(error2Tags).register(meterRegistry);
        errorCounter2.increment();

        // Verify counts
        assertEquals(2.0, processedCounter.count());
        assertEquals(1.0, errorCounter1.count());
        assertEquals(1.0, errorCounter2.count());
    }
}
