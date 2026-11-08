package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.seonwkim.actor.SpringActorSystem.ShutdownOutcome;
import io.github.seonwkim.actor.autoconfigure.ActorSystemShutdownLifecycle;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.pekko.Done;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Tests that closing the Spring context shuts the actor system down through Pekko coordinated
 * shutdown, with a bounded wait, a guard against running twice, and the right order relative to
 * the web server.
 *
 * <p>The application classes here are deliberately not annotated with {@code @Configuration} or
 * {@code @Component}, so other tests' component scans do not pick them up.
 */
class GracefulShutdownTest {

    @EnableActorSupport
    static class LocalApp {}

    // ---------------------------------------------------------------------------------
    // Blocking actor
    // ---------------------------------------------------------------------------------

    static final AtomicReference<CountDownLatch> blockStarted = new AtomicReference<>(new CountDownLatch(1));
    static final AtomicReference<CountDownLatch> blockRelease = new AtomicReference<>(new CountDownLatch(1));

    /** Actor whose message handler blocks until released, which keeps the actor system from terminating. */
    static class BlockingActor implements SpringActor<BlockingActor.Command> {
        public interface Command {}

        public static class Block implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Block.class, (ctx, msg) -> {
                        blockStarted.get().countDown();
                        try {
                            blockRelease.get().await(60, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @EnableActorSupport
    static class BlockingApp {
        @Bean
        public BlockingActor blockingActor() {
            return new BlockingActor();
        }
    }

    // ---------------------------------------------------------------------------------
    // Ordering with a graceful web server
    // ---------------------------------------------------------------------------------

    static final List<String> events = new CopyOnWriteArrayList<>();
    static final AtomicReference<ActorSystem<?>> rawSystem = new AtomicReference<>();

    /** Stands in for an embedded web server; records when Spring Boot asks it to drain. */
    static class FakeWebServer implements WebServer {
        @Override
        public void start() {}

        @Override
        public void stop() {
            events.add("web-stopped");
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public void shutDownGracefully(GracefulShutdownCallback callback) {
            events.add("web-graceful-shutdown");
            callback.shutdownComplete(GracefulShutdownResult.IDLE);
        }
    }

    /** A bean the actors would use; records whether the actor system was already gone when destroyed. */
    static class ActorDependency implements DisposableBean {
        @Override
        public void destroy() {
            ActorSystem<?> system = rawSystem.get();
            boolean terminated = system != null
                    && system.getWhenTerminated().toCompletableFuture().isDone();
            events.add("dependency-destroyed(actor-system-terminated=" + terminated + ")");
        }
    }

    @EnableActorSupport
    static class OrderingApp {
        @Bean
        public WebServerGracefulShutdownLifecycle webServerGracefulShutdownLifecycle() {
            return new WebServerGracefulShutdownLifecycle(new FakeWebServer());
        }

        @Bean
        public ActorDependency actorDependency() {
            return new ActorDependency();
        }

        @Bean
        public SmartInitializingSingleton recordActorShutdown(SpringActorSystem actorSystem) {
            return () -> {
                rawSystem.set(PekkoActorSystem.raw(actorSystem));
                CoordinatedShutdown.get(PekkoActorSystem.raw(actorSystem))
                        .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "record-start", (Supplier<
                                        CompletionStage<Done>>)
                                () -> {
                                    events.add("actor-coordinated-shutdown");
                                    return CompletableFuture.completedFuture(Done.getInstance());
                                });
            };
        }
    }

    private ConfigurableApplicationContext context;

    private static ConfigurableApplicationContext start(Class<?> app, String... properties) {
        return new SpringApplicationBuilder(app)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }

    @AfterEach
    void tearDown() {
        blockRelease.get().countDown();
        if (context != null && context.isActive()) {
            context.close();
        }
    }

    @Test
    void closingContextRunsCoordinatedShutdownAndTerminatesActorSystem() {
        context = start(LocalApp.class);
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        ActorSystemShutdownLifecycle lifecycle = context.getBean(ActorSystemShutdownLifecycle.class);
        assertThat(lifecycle.isRunning()).isTrue();

        context.close();

        assertThat(PekkoActorSystem.raw(system)
                        .getWhenTerminated()
                        .toCompletableFuture()
                        .isDone())
                .isTrue();
        assertThat(CoordinatedShutdown.get(PekkoActorSystem.raw(system)).getShutdownReason())
                .isEqualTo(Optional.of(PekkoActorSystem.SPRING_CONTEXT_CLOSED));
        assertThat(lifecycle.getLastOutcome()).isEqualTo(ShutdownOutcome.TERMINATED);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void closingContextReturnsWithinTimeoutWhenAnActorBlocksShutdown() throws Exception {
        blockStarted.set(new CountDownLatch(1));
        blockRelease.set(new CountDownLatch(1));
        context = start(BlockingApp.class, "spring.actor.shutdown.timeout=1s");
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        ActorSystemShutdownLifecycle lifecycle = context.getBean(ActorSystemShutdownLifecycle.class);

        SpringActorHandle<BlockingActor.Command> blocker = system.actor(BlockingActor.class)
                .withId("blocker")
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        blocker.tell(new BlockingActor.Block());
        assertThat(blockStarted.get().await(5, TimeUnit.SECONDS)).isTrue();

        long startNanos = System.nanoTime();
        context.close();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(900));
        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        assertThat(lifecycle.getLastOutcome()).isEqualTo(ShutdownOutcome.TIMED_OUT);

        // Once the actor is released the actor system finishes terminating.
        blockRelease.get().countDown();
        PekkoActorSystem.raw(system).getWhenTerminated().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    @Test
    void closingContextAfterActorSystemAlreadyTerminatedDoesNothingMore() throws Exception {
        context = start(LocalApp.class);
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        ActorSystemShutdownLifecycle lifecycle = context.getBean(ActorSystemShutdownLifecycle.class);

        // Terminated outside Spring, as happens when the node is downed.
        PekkoActorSystem.raw(system).terminate();
        PekkoActorSystem.raw(system).getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertThatCode(() -> context.close()).doesNotThrowAnyException();

        assertThat(lifecycle.getLastOutcome()).isEqualTo(ShutdownOutcome.ALREADY_TERMINATED);
        assertThat(CoordinatedShutdown.get(PekkoActorSystem.raw(system)).getShutdownReason())
                .isNotEqualTo(Optional.of(PekkoActorSystem.SPRING_CONTEXT_CLOSED));
        assertThat(system.shutdown(Duration.ofSeconds(1))).isEqualTo(ShutdownOutcome.ALREADY_TERMINATED);
    }

    @Test
    void secondShutdownCallDoesNotWaitAgain() throws Exception {
        blockStarted.set(new CountDownLatch(1));
        blockRelease.set(new CountDownLatch(1));
        context = start(BlockingApp.class);
        SpringActorSystem system = context.getBean(SpringActorSystem.class);
        system.actor(BlockingActor.class)
                .withId("blocker")
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .tell(new BlockingActor.Block());
        assertThat(blockStarted.get().await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(system.shutdown(Duration.ofMillis(200))).isEqualTo(ShutdownOutcome.TIMED_OUT);
        assertThat(system.shutdown(Duration.ofSeconds(30))).isEqualTo(ShutdownOutcome.ALREADY_IN_PROGRESS);

        blockRelease.get().countDown();
        PekkoActorSystem.raw(system).getWhenTerminated().toCompletableFuture().get(15, TimeUnit.SECONDS);
        assertThat(system.shutdown(Duration.ofSeconds(1))).isEqualTo(ShutdownOutcome.ALREADY_TERMINATED);
    }

    @Test
    void shutdownTimeoutDefaultsToTwentySeconds() {
        context = start(LocalApp.class);
        assertThat(context.getBean(ActorSystemShutdownLifecycle.class).getTimeout())
                .isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void shutdownTimeoutCanBeOverridden() {
        context = start(LocalApp.class, "spring.actor.shutdown.timeout=12s");
        assertThat(context.getBean(ActorSystemShutdownLifecycle.class).getTimeout())
                .isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void withGracefulWebServerTheWebServerStopsBeforeTheActorSystem() {
        events.clear();
        rawSystem.set(null);
        context = start(OrderingApp.class, "server.shutdown=graceful");

        context.close();

        assertThat(events)
                .containsSubsequence(
                        "web-graceful-shutdown",
                        "actor-coordinated-shutdown",
                        "dependency-destroyed(actor-system-terminated=true)");
        assertThat(ActorSystemShutdownLifecycle.PHASE)
                .isLessThan(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE)
                // WebServerStartStopLifecycle runs 1024 below graceful shutdown since Boot 3.2.
                .isLessThan(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 1024);
    }
}
