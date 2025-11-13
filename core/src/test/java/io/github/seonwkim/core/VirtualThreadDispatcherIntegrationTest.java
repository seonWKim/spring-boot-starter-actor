package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration tests for virtual thread dispatcher configuration. These tests verify that the virtual
 * thread dispatcher can be properly configured and used with actors.
 */
@SpringBootTest(classes = {ActorConfiguration.class, VirtualThreadDispatcherIntegrationTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class VirtualThreadDispatcherIntegrationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Configuration
    static class TestConfig {
        @Bean
        public TestVirtualThreadActor testVirtualThreadActor() {
            return new TestVirtualThreadActor();
        }
    }

    // Test message types
    public interface TestCommand extends FrameworkCommand {}

    public static class Echo extends AskCommand<String> implements TestCommand {
        public final String message;

        public Echo(String message) {
            this.message = message;
        }
    }

    // Test actor
    public static class TestVirtualThreadActor implements SpringActor<TestCommand> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Echo.class, (ctx, msg) -> {
                        msg.reply(msg.message);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    /**
     * Tests that DispatcherConfig.virtualThreads() throws UnsupportedOperationException on Java < 21.
     * On Java 21+, it should return a valid dispatcher configuration.
     */
    @Test
    void testVirtualThreadsAvailability() {
        // Check Java version
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorVersion(javaVersion);

        if (majorVersion >= 21) {
            // Virtual threads should be available
            assertDoesNotThrow(() -> {
                DispatcherConfig config = DispatcherConfig.virtualThreads();
                assertNotNull(config);
                assertTrue(config.shouldUseProps());
                assertEquals("DispatcherConfig.virtualThreads()", config.toString());
            });
        } else {
            // Virtual threads should not be available
            UnsupportedOperationException exception =
                    assertThrows(UnsupportedOperationException.class, () -> {
                        DispatcherConfig.virtualThreads();
                    });
            assertTrue(exception.getMessage().contains("Virtual threads require Java 21 or later"));
            assertTrue(exception.getMessage().contains("Current Java version"));
        }
    }

    /**
     * Tests that the virtual thread dispatcher configuration is properly generated.
     */
    @Test
    void testVirtualThreadDispatcherConfig() {
        var config = DispatcherConfig.getVirtualThreadDispatcherConfig();
        assertNotNull(config);

        // Check Java version to determine expected behavior
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorVersion(javaVersion);

        if (majorVersion >= 21) {
            // Config should be populated on Java 21+
            assertFalse(config.isEmpty());
            assertTrue(config.containsKey("pekko"));
        } else {
            // Config should be empty on Java < 21
            assertTrue(config.isEmpty());
        }
    }

    /**
     * Tests that withVirtualThreadDispatcher() method works correctly on the SpawnBuilder.
     * This test will skip actual spawning on Java < 21 since it will throw an exception.
     */
    @Test
    void testWithVirtualThreadDispatcherMethod() {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorVersion(javaVersion);

        if (majorVersion >= 21) {
            // Should be able to configure the builder with virtual thread dispatcher
            assertDoesNotThrow(() -> {
                var builder = actorSystem
                        .actor(TestVirtualThreadActor.class)
                        .withId("vt-actor-1")
                        .withVirtualThreadDispatcher()
                        .withTimeout(Duration.ofSeconds(5));

                assertNotNull(builder);
            });
        } else {
            // Should throw UnsupportedOperationException on Java < 21
            assertThrows(UnsupportedOperationException.class, () -> {
                actorSystem
                        .actor(TestVirtualThreadActor.class)
                        .withId("vt-actor-2")
                        .withVirtualThreadDispatcher();
            });
        }
    }

    /**
     * Tests spawning an actor with virtual thread dispatcher (only on Java 21+).
     * On Java < 21, this test verifies that the appropriate exception is thrown.
     */
    @Test
    void testSpawnActorWithVirtualThreadDispatcher() throws Exception {
        String javaVersion = System.getProperty("java.version");
        int majorVersion = getMajorVersion(javaVersion);

        if (majorVersion >= 21) {
            // Should be able to spawn actor with virtual thread dispatcher on Java 21+
            CompletionStage<SpringActorRef<TestCommand>> refStage = actorSystem
                    .actor(TestVirtualThreadActor.class)
                    .withId("vt-actor-spawn")
                    .withVirtualThreadDispatcher()
                    .withTimeout(Duration.ofSeconds(5))
                    .spawn();

            SpringActorRef<TestCommand> ref = refStage.toCompletableFuture().get();
            assertNotNull(ref);

            // Test that the actor works correctly
            CompletionStage<String> response = ref.ask(new Echo("Hello Virtual Threads"))
                    .withTimeout(Duration.ofSeconds(5))
                    .execute();

            String result = response.toCompletableFuture().get();
            assertEquals("Hello Virtual Threads", result);

            // Clean up
            ref.stop();
        } else {
            // Should throw exception on Java < 21
            assertThrows(Exception.class, () -> {
                actorSystem
                        .actor(TestVirtualThreadActor.class)
                        .withId("vt-actor-fail")
                        .withVirtualThreadDispatcher()
                        .spawn()
                        .toCompletableFuture()
                        .get();
            });
        }
    }

    /**
     * Tests that DispatcherConfig.fromConfig() still works alongside virtual threads.
     */
    @Test
    void testFromConfigStillWorks() {
        assertDoesNotThrow(() -> {
            DispatcherConfig config = DispatcherConfig.fromConfig("my-custom-dispatcher");
            assertNotNull(config);
            assertTrue(config.shouldUseProps());
            assertEquals("DispatcherConfig.fromConfig(\"my-custom-dispatcher\")", config.toString());
        });
    }

    /**
     * Tests that other dispatcher configurations still work.
     */
    @Test
    void testOtherDispatcherConfigsStillWork() {
        // Test default dispatcher
        assertDoesNotThrow(() -> {
            DispatcherConfig config = DispatcherConfig.defaultDispatcher();
            assertNotNull(config);
            assertFalse(config.shouldUseProps());
        });

        // Test blocking dispatcher
        assertDoesNotThrow(() -> {
            DispatcherConfig config = DispatcherConfig.blocking();
            assertNotNull(config);
            assertTrue(config.shouldUseProps());
        });

        // Test same as parent dispatcher
        assertDoesNotThrow(() -> {
            DispatcherConfig config = DispatcherConfig.sameAsParent();
            assertNotNull(config);
            assertTrue(config.shouldUseProps());
        });
    }

    /**
     * Extracts the major version number from a Java version string.
     */
    private int getMajorVersion(String versionString) {
        try {
            // Handle versions like "11.0.1", "17.0.2", "21.0.0"
            String[] parts = versionString.split("\\.");
            int firstPart = Integer.parseInt(parts[0]);
            
            // For Java 9+, the first part is the major version
            if (firstPart >= 9) {
                return firstPart;
            }
            
            // For Java 8 and earlier (e.g., "1.8.0"), the second part is the major version
            if (parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            
            return firstPart;
        } catch (Exception e) {
            // If parsing fails, assume an older version
            return 11;
        }
    }
}
