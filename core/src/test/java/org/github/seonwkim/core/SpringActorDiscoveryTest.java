package org.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.impl.DefaultActorSystemInstance;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SpringActorDiscoveryTest.TestApp.class)
@TestPropertySource(properties = {
        "actor.pekko.enabled=true",
        "actor.pekko.loglevel=INFO",
        "actor.pekko.actor.provider=local"
})
public class SpringActorDiscoveryTest {

    @SpringBootApplication(scanBasePackages = {
            "org.github.seonwkim.core",
    })
    static class TestApp {}

    @SpringActor
    public static class TestHelloActor {

        public interface Command {}

        public static class SayHello implements TestHelloActor.Command {}

        public static Behavior<TestHelloActor.Command> create(String id) {
            return Behaviors.setup(ctx ->
                                           Behaviors.receive(TestHelloActor.Command.class)
                                                    .onMessage(TestHelloActor.SayHello.class, msg -> {
                                                        System.out.println("Hello from " + id);
                                                        return Behaviors.same();
                                                    })
                                                    .build()
            );
        }
    }

    @Test
    void shouldRegisterSpringActors(ApplicationContext context) {
        assertTrue(context.containsBean("actorTypeRegistry"));

        ActorTypeRegistry registry = context.getBean(ActorTypeRegistry.class);

        // Should be able to create the behavior by command class
        Behavior<TestHelloActor.Command> behavior = registry.createBehavior(TestHelloActor.Command.class,
                                                                            "test-id");

        assertNotNull(behavior, "Behavior for TestHelloActor should be registered and non-null");
    }

    @Test
    void actorSystemShouldStart(ApplicationContext context) {
        assertTrue(context.containsBean("actorSystem"));

        DefaultActorSystemInstance systemInstance = context.getBean(DefaultActorSystemInstance.class);
        assertNotNull(systemInstance.getRaw());
    }
}
