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
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/** An actor bean that Spring wraps in a class-based (CGLIB) proxy is spawned by the class the user declared. */
class ProxiedActorBeanTest {

    /** Its {@code @Async} method makes Spring proxy the bean, as {@code @Transactional} would. */
    static class ProxiedActor implements SpringActor<ProxiedActor.Command> {
        public interface Command {}

        public static class Ping extends AskCommand<String> implements Command {}

        @Async
        public void background() {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .onMessage(Ping.class, (context, msg) -> {
                        msg.reply("pong");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration
    @EnableAsync(proxyTargetClass = true)
    static class Beans {
        @Bean
        ProxiedActor proxiedActor() {
            return new ProxiedActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class));

    @Test
    void aProxiedActorBeanSpawnsByItsDeclaredClass() {
        runner.run(context -> {
            assertThat(AopUtils.isCglibProxy(context.getBean(ProxiedActor.class)))
                    .as("the bean is proxied")
                    .isTrue();

            String reply = context.getBean(SpringActorSystem.class)
                    .actor(ProxiedActor.class)
                    .withId("proxied")
                    .spawn()
                    .thenCompose(actor -> actor.ask(new ProxiedActor.Ping())
                            .withTimeout(Duration.ofSeconds(5))
                            .execute())
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertThat(reply).isEqualTo("pong");
        });
    }
}
