package io.github.seonwkim.metrics.modules.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.testing.TestMetricsBackend;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for MailboxModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class MailboxModuleTest {

    private TestMetricsBackend meterRegistry;
    private MetricsRegistry metricsRegistry;
    private MailboxModule module;

    @BeforeEach
    void setUp() {
        // Create test meter registry
        meterRegistry = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "mailbox")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .meterRegistry(meterRegistry)
                .build();

        // Create and register module
        module = new MailboxModule();
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
        assertEquals("mailbox", module.moduleId());
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
    void testMailboxSizeGaugeCreation() {
        // Simulate creating mailbox size gauge
        AtomicLong mailboxSize = new AtomicLong(0);
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("actor.class", "TestActor"));
        metricsRegistry.getGlobalTags().forEach(tags::add);

        Gauge sizeGauge =
                Gauge.builder("actor.mailbox.size", mailboxSize::get).tags(tags).register(meterRegistry);

        assertNotNull(sizeGauge);
        assertEquals(0.0, sizeGauge.value());

        // Simulate messages being enqueued
        mailboxSize.set(5);
        assertEquals(5.0, sizeGauge.value());

        // Simulate messages being processed
        mailboxSize.set(2);
        assertEquals(2.0, sizeGauge.value());
    }

    @Test
    void testMailboxTimeTimerCreation() {
        // Simulate creating mailbox time timer
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("actor.class", "TestActor"));
        tags.add(Tag.of("message.type", "TestMessage"));
        metricsRegistry.getGlobalTags().forEach(tags::add);

        Timer mailboxTimer = Timer.builder("actor.mailbox.time").tags(tags).register(meterRegistry);
        assertNotNull(mailboxTimer);

        // Record some mailbox times
        mailboxTimer.record(10, TimeUnit.MILLISECONDS);
        mailboxTimer.record(25, TimeUnit.MILLISECONDS);
        mailboxTimer.record(15, TimeUnit.MILLISECONDS);

        assertEquals(3, mailboxTimer.count());
        assertEquals(50, Math.round(mailboxTimer.totalTime(TimeUnit.MILLISECONDS)));
    }

    @Test
    void testMultipleActorMailboxes() {
        // Simulate tracking mailbox sizes for different actor classes
        String[] actorClasses = {"Actor1", "Actor2", "Actor3"};
        Map<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

        for (String actorClass : actorClasses) {
            AtomicLong size = new AtomicLong(0);
            mailboxSizes.put(actorClass, size);

            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of("actor.class", actorClass));
            metricsRegistry.getGlobalTags().forEach(tags::add);
            Gauge gauge = Gauge.builder("actor.mailbox.size", () -> size.get())
                    .tags(tags)
                    .register(meterRegistry);
            assertNotNull(gauge);

            // Set different sizes
            size.set((long) (Math.random() * 10));
        }

        // Verify we have gauges for all actor classes
        assertEquals(
                3,
                meterRegistry.getMeters().stream()
                        .filter(m -> m instanceof Gauge)
                        .count());
    }

    @Test
    void testMailboxTimeForDifferentMessageTypes() {
        String actorClass = "TestActor";
        String[] messageTypes = {"SayHello", "Stop", "GetStatus"};

        for (String messageType : messageTypes) {
            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of("actor.class", actorClass));
            tags.add(Tag.of("message.type", messageType));
            metricsRegistry.getGlobalTags().forEach(tags::add);

            Timer timer = Timer.builder("actor.mailbox.time").tags(tags).register(meterRegistry);
            timer.record((long) (Math.random() * 100), TimeUnit.MILLISECONDS);
        }

        // Verify we have timers for all message types
        assertEquals(
                3,
                meterRegistry.getMeters().stream()
                        .filter(m -> m instanceof Timer)
                        .count());
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
    void testMailboxSizeDynamicUpdates() {
        AtomicLong mailboxSize = new AtomicLong(0);
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("actor.class", "DynamicActor"));
        metricsRegistry.getGlobalTags().forEach(tags::add);

        Gauge gauge = Gauge.builder("actor.mailbox.size", () -> mailboxSize.get())
                .tags(tags)
                .register(meterRegistry);

        // Simulate enqueue operations
        mailboxSize.incrementAndGet();
        assertEquals(1.0, gauge.value());

        mailboxSize.incrementAndGet();
        mailboxSize.incrementAndGet();
        assertEquals(3.0, gauge.value());

        // Simulate dequeue operations
        mailboxSize.decrementAndGet();
        assertEquals(2.0, gauge.value());

        mailboxSize.set(0);
        assertEquals(0.0, gauge.value());
    }
}
