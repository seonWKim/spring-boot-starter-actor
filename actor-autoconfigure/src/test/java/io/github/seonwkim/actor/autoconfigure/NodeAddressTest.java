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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The system and every actor report this node's address (the cluster case is in actor-cluster's tests). */
class NodeAddressTest {

    static class AddressActor implements SpringActor<AddressActor.Command> {
        public interface Command {}

        public static class WhereAmI extends AskCommand<String> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(WhereAmI.class, (context, msg) -> {
                        msg.reply(context.nodeAddress());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        AddressActor addressActor() {
            return new AddressActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class))
            .withPropertyValues("spring.actor.name=orders");

    private static String askActor(SpringActorSystem system) throws Exception {
        return system.actor(AddressActor.class)
                .withId("where")
                .spawn()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .ask(new AddressActor.WhereAmI())
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void aLocalSystemIsAddressedByItsName() {
        runner.run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);
            assertThat(system.nodeAddress()).isEqualTo("pekko://orders");
            assertThat(askActor(system)).isEqualTo("pekko://orders");
        });
    }
}
