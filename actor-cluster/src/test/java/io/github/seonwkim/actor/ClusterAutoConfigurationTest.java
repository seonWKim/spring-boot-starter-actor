package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/** With actor-cluster on the classpath, {@code @EnableActorSupport} starts cluster mode and sharding. */
@SpringBootTest(classes = {ClusterAutoConfigurationTest.TestApp.class, ClusterAutoConfigurationTest.Room.class})
@TestPropertySource(
        properties = {
            "spring.actor.pekko.actor.provider=cluster",
            "spring.actor.pekko.remote.artery.canonical.port=0",
            "spring.actor.pekko.management.enabled=false"
        })
class ClusterAutoConfigurationTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    /** A sharded actor that injects the actor system: registering it must not create a cycle. */
    @Component
    static class Room implements SpringShardedActor<Room.Command> {
        static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "auto-config-room");

        final SpringActorSystem actorSystem;

        Room(SpringActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        interface Command extends JsonSerializable {}

        @Override
        public ShardedEntityType<Command> entityType() {
            return TYPE;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringActorBehavior.builder(Command.class, ctx).build();
        }
    }

    @Autowired
    SpringActorSystem actorSystem;

    @Autowired
    Room room;

    @Autowired
    ShardedActorRegistry shardedActors;

    @Test
    void theClusterModuleIsFoundAndStartsClusterMode() {
        assertThat(actorSystem.isClusterMode()).isTrue();
        assertThat(actorSystem.unwrap(Cluster.class)).isNotNull();
    }

    @Test
    void shardedActorBeansAreRegisteredAndReachable() {
        assertThat(shardedActors.get(Room.TYPE)).isSameAs(room);
        assertThat(actorSystem.sharded(Room.class).withId("r1").get().entityId())
                .isEqualTo("r1");
    }
}
