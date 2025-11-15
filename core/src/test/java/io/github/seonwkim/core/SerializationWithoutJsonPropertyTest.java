package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import io.github.seonwkim.core.shard.SpringShardedActorRef;
import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Tests that verify serialization works without @JsonProperty annotations.
 *
 * <p>This test creates sharded actors with messages that do NOT use @JsonProperty annotations,
 * relying instead on the ParameterNamesModule to infer parameter names from bytecode.
 */
public class SerializationWithoutJsonPropertyTest extends AbstractClusterTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class TestApp {
        @Bean
        public NoAnnotationShardedActor noAnnotationShardedActor() {
            return new NoAnnotationShardedActor();
        }
    }

    /**
     * A test actor that uses messages WITHOUT @JsonProperty annotations.
     * This demonstrates that the ParameterNamesModule configuration works correctly.
     */
    public static class NoAnnotationShardedActor implements SpringShardedActor<NoAnnotationShardedActor.Command> {
        public static final EntityTypeKey<Command> TYPE_KEY =
                EntityTypeKey.create(Command.class, "NoAnnotationActor");

        public interface Command extends JsonSerializable {}

        /**
         * Message with a single parameter - NO @JsonProperty annotation.
         */
        public static class Echo extends AskCommand<String> implements Command {
            public final String message;

            public Echo(String message) {
                this.message = message;
            }
        }

        /**
         * Message with multiple parameters - NO @JsonProperty annotations.
         */
        public static class Calculate extends AskCommand<Integer> implements Command {
            public final int a;
            public final int b;
            public final String operation;

            public Calculate(int a, int b, String operation) {
                this.a = a;
                this.b = b;
                this.operation = operation;
            }
        }

        /**
         * Complex message with nested data - NO @JsonProperty annotations.
         */
        public static class StoreData implements Command {
            public final String key;
            public final DataValue value;

            public StoreData(String key, DataValue value) {
                this.key = key;
                this.value = value;
            }
        }

        public static class DataValue {
            public final String text;
            public final int number;

            public DataValue(String text, int number) {
                this.text = text;
                this.number = number;
            }
        }

        public static class GetData extends AskCommand<DataValue> implements Command {
            public final String key;

            public GetData(String key) {
                this.key = key;
            }
        }

        @Override
        public EntityTypeKey<Command> typeKey() {
            return TYPE_KEY;
        }

        @Override
        public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringShardedActorBehavior.builder(Command.class, ctx)
                    .withState(actorCtx -> new ActorState())
                    .onMessage(Echo.class, (state, msg) -> {
                        msg.reply("Echo: " + msg.message);
                        return Behaviors.same();
                    })
                    .onMessage(Calculate.class, (state, msg) -> {
                        int result;
                        if ("add".equals(msg.operation)) {
                            result = msg.a + msg.b;
                        } else if ("multiply".equals(msg.operation)) {
                            result = msg.a * msg.b;
                        } else {
                            result = 0;
                        }
                        msg.reply(result);
                        return Behaviors.same();
                    })
                    .onMessage(StoreData.class, (state, msg) -> {
                        state.storedValue = msg.value;
                        return Behaviors.same();
                    })
                    .onMessage(GetData.class, (state, msg) -> {
                        msg.reply(state.storedValue);
                        return Behaviors.same();
                    })
                    .build();
        }

        private static class ActorState {
            private DataValue storedValue;
        }

        @Override
        public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
            return new DefaultShardingMessageExtractor<>(3);
        }
    }

    @Override
    protected Class<?> getApplicationClass() {
        return TestApp.class;
    }

    @Test
    void testSimpleMessageWithoutJsonProperty() throws Exception {
        waitUntilClusterInitialized();

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringShardedActorRef<NoAnnotationShardedActor.Command> actor =
                system1.sharded(NoAnnotationShardedActor.class)
                        .withId("test-entity-1")
                        .get();

        String response = actor.ask(new NoAnnotationShardedActor.Echo("Hello World"))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals("Echo: Hello World", response);
    }

    @Test
    void testMultiParameterMessageWithoutJsonProperty() throws Exception {
        waitUntilClusterInitialized();

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringShardedActorRef<NoAnnotationShardedActor.Command> actor =
                system1.sharded(NoAnnotationShardedActor.class)
                        .withId("test-entity-2")
                        .get();

        Integer result = actor.ask(new NoAnnotationShardedActor.Calculate(5, 3, "add"))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals(8, result);

        Integer result2 = actor.ask(new NoAnnotationShardedActor.Calculate(5, 3, "multiply"))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        assertEquals(15, result2);
    }
}
