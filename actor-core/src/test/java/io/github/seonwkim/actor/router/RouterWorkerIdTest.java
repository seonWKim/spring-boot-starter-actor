package io.github.seonwkim.actor.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Every worker of a router pool gets its own actor id (#190). */
class RouterWorkerIdTest {

    interface Command {}

    record Hello() implements Command {}

    static class Worker implements SpringActor<Command> {
        final Set<String> seenIds = ConcurrentHashMap.newKeySet();

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Hello.class, (ctx, msg) -> {
                        seenIds.add(actorContext.actorId());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    static class Router implements SpringActor<Command> {
        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringRouterBehavior.builder(Command.class, actorContext)
                    .withRoutingStrategy(RoutingStrategy.broadcast())
                    .withPoolSize(3)
                    .withWorkerActors(Worker.class)
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        Worker worker() {
            return new Worker();
        }

        @Bean
        Router router() {
            return new Router();
        }
    }

    @Test
    void eachWorkerOfAPoolHasItsOwnId() {
        new ApplicationContextRunner()
                .withUserConfiguration(App.class)
                .withPropertyValues("spring.actor.pekko.actor.provider=local")
                .run(context -> {
                    context.getBean(SpringActorSystem.class)
                            .actor(Router.class)
                            .withId("router")
                            .spawnAndWait()
                            .tell(new Hello());

                    Set<String> seenIds = context.getBean(Worker.class).seenIds;
                    await().atMost(Duration.ofSeconds(5)).until(() -> seenIds.size() == 3);
                    assertThat(seenIds).allMatch(id -> id.startsWith("worker-"));
                });
    }
}
