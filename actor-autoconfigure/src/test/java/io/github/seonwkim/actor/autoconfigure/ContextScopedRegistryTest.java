package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/** Two Spring contexts in one JVM each spawn their own actor beans, even for the same actor class. */
class ContextScopedRegistryTest {

    static class GreeterActor implements SpringActor<GreeterActor.Command> {
        public interface Command {}

        public static class Greet extends AskCommand<String> implements Command {}

        private final String greeting;

        GreeterActor(String greeting) {
            this.greeting = greeting;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(Greet.class, (context, msg) -> {
                        msg.reply(greeting);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    private static ApplicationContextRunner contextGreeting(String greeting) {
        return new ApplicationContextRunner()
                .withConfiguration(UserConfigurations.of(ActorConfiguration.class))
                .withBean(GreeterActor.class, () -> new GreeterActor(greeting));
    }

    private static String greet(ApplicationContext context) throws Exception {
        return context.getBean(SpringActorSystem.class)
                .actor(GreeterActor.class)
                .withId("greeter")
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .ask(new GreeterActor.Greet())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void eachContextSpawnsItsOwnBeanOfTheSameActorClass() {
        contextGreeting("from first")
                .run(first -> contextGreeting("from second").run(second -> {
                    assertThat(greet(first)).isEqualTo("from first");
                    assertThat(greet(second)).isEqualTo("from second");
                }));
    }
}
