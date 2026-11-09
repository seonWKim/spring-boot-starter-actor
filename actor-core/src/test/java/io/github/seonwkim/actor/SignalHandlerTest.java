package io.github.seonwkim.actor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/** The starter's {@link PreRestart} and {@link PostStop} reach the handlers registered for them. */
@SpringBootTest(classes = {ActorConfiguration.class, SignalHandlerTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SignalHandlerTest {

    static final Queue<String> events = new ConcurrentLinkedQueue<>();

    @Autowired
    private SpringActorSystem actorSystem;

    @Test
    void preRestartHandlerRunsBeforeASupervisedRestart() {
        actorSystem.actor(SignalActor.class).withId("restarts").spawnAndWait().tell(new Fail());

        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("restarts:pre-restart"));
    }

    @Test
    void postStopHandlerRunsWhenAHandlerReturnsStopped() {
        actorSystem.actor(SignalActor.class).withId("stops").spawnAndWait().tell(new Stop());

        await().atMost(Duration.ofSeconds(5)).until(() -> events.contains("stops:post-stop"));
        assertTrue(!events.contains("stops:pre-restart"), events.toString());
    }

    interface Command {}

    record Fail() implements Command {}

    record Stop() implements Command {}

    static class SignalActor implements SpringActor<Command> {
        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            String id = actorContext.actorId();
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withSupervisionStrategy(SupervisionStrategy.restart())
                    .onMessage(Fail.class, (ctx, msg) -> {
                        throw new IllegalStateException("failing on purpose");
                    })
                    .onMessage(Stop.class, (ctx, msg) -> SpringActorBehavior.stopped())
                    .onSignal(PreRestart.class, (ctx, signal) -> {
                        events.add(id + ":pre-restart");
                        return SpringActorBehavior.same();
                    })
                    .onSignal(PostStop.class, (ctx, signal) -> {
                        events.add(id + ":post-stop");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        SignalActor signalActor() {
            return new SignalActor();
        }
    }
}
