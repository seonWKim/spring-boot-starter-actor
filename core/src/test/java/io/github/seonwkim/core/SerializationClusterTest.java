package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.seonwkim.core.serialization.CborSerializable;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.*;
import java.time.Duration;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Tests to verify that both JacksonJsonSerializer and JacksonCborSerializer
 * properly serialize and deserialize messages across cluster nodes.
 *
 * <p>This test verifies:
 * - Messages without ActorRef work with JSON serialization
 * - Messages with ActorRef (ask pattern) work with JSON serialization
 * - Messages without ActorRef work with CBOR serialization
 * - Messages with ActorRef (ask pattern) work with CBOR serialization
 * - Mixed serializers (JSON and CBOR) work on the same entity
 *
 * <p>All scenarios are combined into a single test to minimize cluster setup overhead.
 */
public class SerializationClusterTest extends AbstractClusterTest {

    /**
     * Test actor for verifying serialization across clusters.
     * Supports both JSON and CBOR serialization for messages with and without ActorRef.
     */
    public static class SerializationTestActor implements SpringShardedActor<SerializationTestActor.Command> {

        public static final EntityTypeKey<SerializationTestActor.Command> TYPE_KEY =
                EntityTypeKey.create(SerializationTestActor.Command.class, "SerializationTestActor");

        // Base command interface
        public interface Command {}

        // ========== JSON Serializable Messages ==========

        /** JSON message without ActorRef */
        public static class JsonMessageWithoutRef implements SerializationTestActor.Command, JsonSerializable {
            public final String data;
            public final int value;

            public JsonMessageWithoutRef(String data, int value) {
                this.data = data;
                this.value = value;
            }
        }

        /** JSON message with ActorRef (using Ask pattern) */
        public static class JsonMessageWithRef extends AskCommand<SerializationTestActor.JsonResponse>
                implements SerializationTestActor.Command, JsonSerializable {
            public final String data;
            public final int value;

            public JsonMessageWithRef(String data, int value) {
                this.data = data;
                this.value = value;
            }
        }

        /** JSON response message */
        public static class JsonResponse implements JsonSerializable {
            public final String result;
            public final int processedValue;

            public JsonResponse(String result, int processedValue) {
                this.result = result;
                this.processedValue = processedValue;
            }
        }

        // ========== CBOR Serializable Messages ==========

        /** CBOR message without ActorRef */
        public static class CborMessageWithoutRef implements SerializationTestActor.Command, CborSerializable {
            public final String data;
            public final int value;

            public CborMessageWithoutRef(String data, int value) {
                this.data = data;
                this.value = value;
            }
        }

        /** CBOR message with ActorRef (using Ask pattern) */
        public static class CborMessageWithRef extends AskCommand<SerializationTestActor.CborResponse>
                implements SerializationTestActor.Command, CborSerializable {
            public final String data;
            public final int value;

            public CborMessageWithRef(String data, int value) {
                this.data = data;
                this.value = value;
            }
        }

        /** CBOR response message */
        public static class CborResponse implements CborSerializable {
            public final String result;
            public final int processedValue;

            public CborResponse(String result, int processedValue) {
                this.result = result;
                this.processedValue = processedValue;
            }
        }

        // ========== State Tracking ==========

        private static class ActorState {
            private final SpringBehaviorContext<SerializationTestActor.Command> context;
            private final String entityId;
            private int jsonMessagesReceived = 0;
            private int cborMessagesReceived = 0;

            ActorState(SpringBehaviorContext<SerializationTestActor.Command> context, String entityId) {
                this.context = context;
                this.entityId = entityId;
            }
        }

        @Override
        public EntityTypeKey<SerializationTestActor.Command> typeKey() {
            return TYPE_KEY;
        }

        @Override
        public SpringShardedActorBehavior<SerializationTestActor.Command> create(
                SpringShardedActorContext<SerializationTestActor.Command> ctx) {
            return SpringShardedActorBehavior.builder(SerializationTestActor.Command.class, ctx)
                    .withState(context -> new SerializationTestActor.ActorState(context, ctx.getEntityId()))
                    .onMessage(SerializationTestActor.JsonMessageWithoutRef.class, (state, msg) -> {
                        state.jsonMessagesReceived++;
                        state.context
                                .getLog()
                                .info(
                                        "Entity [{}] received JsonMessageWithoutRef: data={}, value={}, total={}",
                                        state.entityId,
                                        msg.data,
                                        msg.value,
                                        state.jsonMessagesReceived);
                        return Behaviors.same();
                    })
                    .onMessage(SerializationTestActor.JsonMessageWithRef.class, (state, msg) -> {
                        state.jsonMessagesReceived++;
                        state.context
                                .getLog()
                                .info(
                                        "Entity [{}] received JsonMessageWithRef: data={}, value={}, total={}",
                                        state.entityId,
                                        msg.data,
                                        msg.value,
                                        state.jsonMessagesReceived);
                        // Reply with processed data
                        msg.reply(new SerializationTestActor.JsonResponse("Processed: " + msg.data, msg.value * 2));
                        return Behaviors.same();
                    })
                    .onMessage(SerializationTestActor.CborMessageWithoutRef.class, (state, msg) -> {
                        state.cborMessagesReceived++;
                        state.context
                                .getLog()
                                .info(
                                        "Entity [{}] received CborMessageWithoutRef: data={}, value={}, total={}",
                                        state.entityId,
                                        msg.data,
                                        msg.value,
                                        state.cborMessagesReceived);
                        return Behaviors.same();
                    })
                    .onMessage(SerializationTestActor.CborMessageWithRef.class, (state, msg) -> {
                        state.cborMessagesReceived++;
                        state.context
                                .getLog()
                                .info(
                                        "Entity [{}] received CborMessageWithRef: data={}, value={}, total={}",
                                        state.entityId,
                                        msg.data,
                                        msg.value,
                                        state.cborMessagesReceived);
                        // Reply with processed data
                        msg.reply(new SerializationTestActor.CborResponse("Processed: " + msg.data, msg.value * 2));
                        return Behaviors.same();
                    })
                    .build();
        }

        @Override
        public ShardingMessageExtractor<ShardEnvelope<SerializationTestActor.Command>, SerializationTestActor.Command>
                extractor() {
            return new DefaultShardingMessageExtractor<>(5);
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class SerializationTestApp {
        @Bean
        public SerializationTestActor serializationTestActor() {
            return new SerializationTestActor();
        }
    }

    @Override
    protected Class<?> getApplicationClass() {
        return SerializationTestApp.class;
    }

    @Test
    void allSerializersWorkAcrossCluster() throws Exception {
        // Setup cluster once
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        // ========== Test 1: JSON Serializer without ActorRef ==========
        SpringShardedActorRef<SerializationTestActor.Command> jsonActor1 = system1.sharded(SerializationTestActor.class)
                .withId("json-entity-1")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> jsonActor2 = system2.sharded(SerializationTestActor.class)
                .withId("json-entity-2")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> jsonActor3 = system3.sharded(SerializationTestActor.class)
                .withId("json-entity-3")
                .get();

        // Send JSON messages without ActorRef - these use JacksonJsonSerializer
        jsonActor1.tell(new SerializationTestActor.JsonMessageWithoutRef("json-tell-1", 100));
        jsonActor2.tell(new SerializationTestActor.JsonMessageWithoutRef("json-tell-2", 200));
        jsonActor3.tell(new SerializationTestActor.JsonMessageWithoutRef("json-tell-3", 300));

        Thread.sleep(500); // Allow messages to be processed

        // ========== Test 2: JSON Serializer with ActorRef (Ask Pattern) ==========
        SpringShardedActorRef<SerializationTestActor.Command> jsonAskActor1 = system1.sharded(
                        SerializationTestActor.class)
                .withId("json-ask-1")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> jsonAskActor2 = system2.sharded(
                        SerializationTestActor.class)
                .withId("json-ask-2")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> jsonAskActor3 = system3.sharded(
                        SerializationTestActor.class)
                .withId("json-ask-3")
                .get();

        // Send JSON messages with ActorRef using ask pattern
        SerializationTestActor.JsonResponse jsonResp1 = jsonAskActor1
                .ask(new SerializationTestActor.JsonMessageWithRef("json-ask-1", 100))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        SerializationTestActor.JsonResponse jsonResp2 = jsonAskActor2
                .ask(new SerializationTestActor.JsonMessageWithRef("json-ask-2", 200))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        SerializationTestActor.JsonResponse jsonResp3 = jsonAskActor3
                .ask(new SerializationTestActor.JsonMessageWithRef("json-ask-3", 300))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        // Verify JSON responses
        assertEquals("Processed: json-ask-1", jsonResp1.result);
        assertEquals(200, jsonResp1.processedValue);
        assertEquals("Processed: json-ask-2", jsonResp2.result);
        assertEquals(400, jsonResp2.processedValue);
        assertEquals("Processed: json-ask-3", jsonResp3.result);
        assertEquals(600, jsonResp3.processedValue);

        // ========== Test 3: CBOR Serializer without ActorRef ==========
        SpringShardedActorRef<SerializationTestActor.Command> cborActor1 = system1.sharded(SerializationTestActor.class)
                .withId("cbor-entity-1")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> cborActor2 = system2.sharded(SerializationTestActor.class)
                .withId("cbor-entity-2")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> cborActor3 = system3.sharded(SerializationTestActor.class)
                .withId("cbor-entity-3")
                .get();

        // Send CBOR messages without ActorRef - these use JacksonCborSerializer
        cborActor1.tell(new SerializationTestActor.CborMessageWithoutRef("cbor-tell-1", 100));
        cborActor2.tell(new SerializationTestActor.CborMessageWithoutRef("cbor-tell-2", 200));
        cborActor3.tell(new SerializationTestActor.CborMessageWithoutRef("cbor-tell-3", 300));

        Thread.sleep(500); // Allow messages to be processed

        // ========== Test 4: CBOR Serializer with ActorRef (Ask Pattern) ==========
        SpringShardedActorRef<SerializationTestActor.Command> cborAskActor1 = system1.sharded(
                        SerializationTestActor.class)
                .withId("cbor-ask-1")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> cborAskActor2 = system2.sharded(
                        SerializationTestActor.class)
                .withId("cbor-ask-2")
                .get();

        SpringShardedActorRef<SerializationTestActor.Command> cborAskActor3 = system3.sharded(
                        SerializationTestActor.class)
                .withId("cbor-ask-3")
                .get();

        // Send CBOR messages with ActorRef using ask pattern
        SerializationTestActor.CborResponse cborResp1 = cborAskActor1
                .ask(new SerializationTestActor.CborMessageWithRef("cbor-ask-1", 100))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        SerializationTestActor.CborResponse cborResp2 = cborAskActor2
                .ask(new SerializationTestActor.CborMessageWithRef("cbor-ask-2", 200))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        SerializationTestActor.CborResponse cborResp3 = cborAskActor3
                .ask(new SerializationTestActor.CborMessageWithRef("cbor-ask-3", 300))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        // Verify CBOR responses
        assertEquals("Processed: cbor-ask-1", cborResp1.result);
        assertEquals(200, cborResp1.processedValue);
        assertEquals("Processed: cbor-ask-2", cborResp2.result);
        assertEquals(400, cborResp2.processedValue);
        assertEquals("Processed: cbor-ask-3", cborResp3.result);
        assertEquals(600, cborResp3.processedValue);

        // ========== Test 5: Mixed Serializers on Same Entity ==========
        SpringShardedActorRef<SerializationTestActor.Command> mixedJson =
                system1.sharded(SerializationTestActor.class).withId("mixed").get();

        SpringShardedActorRef<SerializationTestActor.Command> mixedCbor =
                system2.sharded(SerializationTestActor.class).withId("mixed").get();

        // Send both JSON and CBOR messages to the same entity
        SerializationTestActor.JsonResponse mixedJsonResp = mixedJson
                .ask(new SerializationTestActor.JsonMessageWithRef("mixed-json", 50))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        SerializationTestActor.CborResponse mixedCborResp = mixedCbor
                .ask(new SerializationTestActor.CborMessageWithRef("mixed-cbor", 75))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        // Verify mixed serializer responses
        assertEquals("Processed: mixed-json", mixedJsonResp.result);
        assertEquals(100, mixedJsonResp.processedValue);
        assertEquals("Processed: mixed-cbor", mixedCborResp.result);
        assertEquals(150, mixedCborResp.processedValue);
    }
}
