package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A bean may spawn an actor in its constructor, even before Spring has created that actor's bean (#214). */
class SpawnDuringStartupTest {

    static class EchoActor implements SpringActor<EchoActor.Command> {

        public interface Command {}

        public static class Echo extends AskCommand<String> implements Command {
            final String text;

            public Echo(String text) {
                this.text = text;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Echo.class, (ctx, msg) -> {
                        msg.reply(msg.text);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    static class SpawningService {
        final SpringActorHandle<EchoActor.Command> echo;

        SpawningService(SpringActorSystem actorSystem) {
            this.echo = actorSystem.actor(EchoActor.class).withId("echo").spawnAndWait();
        }
    }

    /** The service is declared, and so created, before the actor bean it spawns. */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        SpawningService spawningService(SpringActorSystem actorSystem) {
            return new SpawningService(actorSystem);
        }

        @Bean
        EchoActor echoActor() {
            return new EchoActor();
        }
    }

    @Test
    void aBeanCanSpawnInItsConstructorBeforeTheActorBeanExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(App.class)
                .withPropertyValues("spring.actor.pekko.actor.provider=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    String reply = context.getBean(SpawningService.class)
                            .echo
                            .ask(new EchoActor.Echo("hi"))
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .join();
                    assertThat(reply).isEqualTo("hi");
                });
    }
}
