package io.github.seonwkim.actor.topic;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * An actor gets a topic with {@code getOrCreateAsync()} without blocking its thread (#190). The
 * actor system has one thread, so a call that blocks it waits for the root guardian, which needs that
 * same thread, until it times out.
 */
class TopicFromInsideAnActorTest {

    interface Command {}

    static class GetTopic extends AskCommand<String> implements Command {}

    record TopicReady(GetTopic request, SpringTopicRef<String> topic, Throwable error) implements Command {}

    static class TopicUser implements SpringActor<Command> {
        private final SpringTopicManager topics;

        TopicUser(SpringTopicManager topics) {
            this.topics = topics;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(GetTopic.class, (ctx, msg) -> {
                        ctx.pipeToSelf(
                                topics.topic(TopicName.of(String.class, "from-actor"))
                                        .withTimeout(Duration.ofSeconds(2))
                                        .getOrCreateAsync(),
                                (topic, error) -> new TopicReady(msg, topic, error));
                        return SpringActorBehavior.same();
                    })
                    .onMessage(TopicReady.class, (ctx, ready) -> {
                        ready.request()
                                .reply(
                                        ready.error() == null
                                                ? ready.topic().name().name()
                                                : "failed");
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class App {
        @Bean
        TopicUser topicUser(SpringTopicManager topics) {
            return new TopicUser(topics);
        }
    }

    @Test
    void anActorOnASingleThreadGetsATopicAsynchronously() {
        new ApplicationContextRunner()
                .withUserConfiguration(App.class)
                .withPropertyValues(
                        "spring.actor.pekko.actor.provider=local",
                        "spring.actor.pekko.actor.default-dispatcher.executor=thread-pool-executor",
                        "spring.actor.pekko.actor.default-dispatcher.thread-pool-executor.fixed-pool-size=1")
                .run(context -> {
                    String name = context.getBean(SpringActorSystem.class)
                            .actor(TopicUser.class)
                            .withId("topic-user")
                            .spawnAndWait()
                            .ask(new GetTopic())
                            .withTimeout(Duration.ofSeconds(5))
                            .execute()
                            .toCompletableFuture()
                            .join();
                    assertThat(name).isEqualTo("from-actor");
                });
    }
}
