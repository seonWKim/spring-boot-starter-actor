package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import io.github.seonwkim.core.shard.SpringShardedActorHandle;
import java.time.Duration;
import java.util.Optional;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Integration test for role-based cluster sharding.
 *
 * <p>This test verifies that entities with specific roles are only created on nodes with matching
 * roles. On nodes without matching roles, proxies are created that forward messages to the correct
 * node.
 *
 * <p>Cluster configuration:
 * <ul>
 *   <li>Node 1 (seed): No specific role, acts as proxy for role-specific actors
 *   <li>Node 2: "worker" role, hosts WorkerRoleShardedActor entities
 *   <li>Node 3: "coordinator" role, hosts CoordinatorRoleShardedActor entities
 * </ul>
 */
public class RoleBasedClusterShardingTest extends AbstractClusterTest {

    /** Number of test entities to create for each actor type */
    private static final int TEST_ENTITY_COUNT = 10;

    /** Number of shards for sharded actors in this test */
    private static final int SHARD_COUNT = 5;

    /**
     * Base class for node information responses. Contains the actor's node address and port.
     */
    public static class NodeInfo implements JsonSerializable {
        private final String actorPath;
        private final int port;

        @JsonCreator
        public NodeInfo(String actorPath, int port) {
            this.actorPath = actorPath;
            this.port = port;
        }

        public String getActorPath() {
            return actorPath;
        }

        public int getPort() {
            return port;
        }
    }

    /**
     * Base command for getting node information from an actor.
     *
     * @param <T> The command type
     */
    public abstract static class GetNodeInfoCommand<T> extends AskCommand<NodeInfo> implements JsonSerializable {
        public GetNodeInfoCommand() {}
    }

    /**
     * Extracts the port number from a Pekko actor system address.
     *
     * @param address The address in format "pekko://system@127.0.0.1:25520"
     * @return The port number, or -1 if unable to parse
     */
    private static int extractPortFromAddress(String address) {
        int atIndex = address.indexOf('@');
        int colonIndex = address.indexOf(':', atIndex);
        if (atIndex != -1 && colonIndex != -1) {
            String portStr = address.substring(colonIndex + 1);
            return Integer.parseInt(portStr);
        }
        return -1;
    }

    /**
     * Test sharded actor that requires the "worker" role. This actor will only be created on nodes
     * with the "worker" role. On other nodes, a proxy will be created instead.
     */
    public static class WorkerRoleShardedActor implements SpringShardedActor<WorkerRoleShardedActor.Command> {
        public static final EntityTypeKey<Command> TYPE_KEY =
                EntityTypeKey.create(Command.class, "WorkerRoleShardedActor");

        public interface Command extends JsonSerializable {}

        public static class Process implements Command {
            public final String task;

            @JsonCreator
            public Process(String task) {
                this.task = task;
            }
        }

        public static class GetNodeInfo extends GetNodeInfoCommand<Command> implements Command {}

        @Override
        public EntityTypeKey<Command> typeKey() {
            return TYPE_KEY;
        }

        @Override
        public Optional<String> role() {
            return Optional.of("worker");
        }

        @Override
        public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringShardedActorBehavior.builder(Command.class, ctx)
                    .withState(context -> new ActorState(context, ctx.getEntityId()))
                    .onMessage(Process.class, (state, msg) -> {
                        state.context.getLog().info("Worker actor {} processed task: {}", state.entityId, msg.task);
                        return Behaviors.same();
                    })
                    .onMessage(GetNodeInfo.class, (state, cmd) -> {
                        // Get the address from the actor system (this is the node's address)
                        String address = state.context
                                .getUnderlying()
                                .getSystem()
                                .address()
                                .toString();
                        int port = RoleBasedClusterShardingTest.extractPortFromAddress(address);
                        cmd.reply(new RoleBasedClusterShardingTest.NodeInfo(address, port));
                        return Behaviors.same();
                    })
                    .build();
        }

        private static class ActorState {
            private final SpringBehaviorContext<Command> context;
            private final String entityId;

            ActorState(SpringBehaviorContext<Command> context, String entityId) {
                this.context = context;
                this.entityId = entityId;
            }
        }

        @Override
        public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
            return new DefaultShardingMessageExtractor<>(SHARD_COUNT);
        }
    }

    /**
     * Test sharded actor that requires the "coordinator" role. This actor will only be created on
     * nodes with the "coordinator" role. On other nodes, a proxy will be created instead.
     */
    public static class CoordinatorRoleShardedActor implements SpringShardedActor<CoordinatorRoleShardedActor.Command> {
        public static final EntityTypeKey<Command> TYPE_KEY =
                EntityTypeKey.create(Command.class, "CoordinatorRoleShardedActor");

        public interface Command extends JsonSerializable {}

        public static class Coordinate implements Command {
            public final String operation;

            @JsonCreator
            public Coordinate(String operation) {
                this.operation = operation;
            }
        }

        public static class GetNodeInfo extends GetNodeInfoCommand<Command> implements Command {}

        @Override
        public EntityTypeKey<Command> typeKey() {
            return TYPE_KEY;
        }

        @Override
        public Optional<String> role() {
            return Optional.of("coordinator");
        }

        @Override
        public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringShardedActorBehavior.builder(Command.class, ctx)
                    .withState(context -> new ActorState(context, ctx.getEntityId()))
                    .onMessage(Coordinate.class, (state, msg) -> {
                        state.context
                                .getLog()
                                .info("Coordinator actor {} coordinated operation: {}", state.entityId, msg.operation);
                        return Behaviors.same();
                    })
                    .onMessage(GetNodeInfo.class, (state, cmd) -> {
                        // Get the address from the actor system (this is the node's address)
                        String address = state.context
                                .getUnderlying()
                                .getSystem()
                                .address()
                                .toString();
                        int port = RoleBasedClusterShardingTest.extractPortFromAddress(address);
                        cmd.reply(new RoleBasedClusterShardingTest.NodeInfo(address, port));
                        return Behaviors.same();
                    })
                    .build();
        }

        private static class ActorState {
            private final SpringBehaviorContext<Command> context;
            private final String entityId;

            ActorState(SpringBehaviorContext<Command> context, String entityId) {
                this.context = context;
                this.entityId = entityId;
            }
        }

        @Override
        public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
            return new DefaultShardingMessageExtractor<>(SHARD_COUNT);
        }
    }

    @SpringBootApplication
    public static class RoleBasedClusterTestApp {
        @Bean
        public WorkerRoleShardedActor workerRoleShardedActor() {
            return new WorkerRoleShardedActor();
        }

        @Bean
        public CoordinatorRoleShardedActor coordinatorRoleShardedActor() {
            return new CoordinatorRoleShardedActor();
        }
    }

    @Override
    protected Class<?> getApplicationClass() {
        return RoleBasedClusterTestApp.class;
    }

    @Override
    protected String getActorSystemName() {
        return "role-test-cluster";
    }

    @Override
    protected String[] getNode1Roles() {
        return new String[0]; // No role (seed node, acts as proxy)
    }

    @Override
    protected String[] getNode2Roles() {
        return new String[] {"worker"}; // Hosts WorkerRoleShardedActor entities
    }

    @Override
    protected String[] getNode3Roles() {
        return new String[] {"coordinator"}; // Hosts CoordinatorRoleShardedActor entities
    }

    @Test
    void entityWithRoleIsCreatedOnlyOnNodeWithMatchingRole() throws Exception {
        waitUntilClusterInitialized();

        SpringActorSystem systemSeed = context1.getBean(SpringActorSystem.class);
        SpringActorSystem systemWorker = context2.getBean(SpringActorSystem.class);
        SpringActorSystem systemCoordinator = context3.getBean(SpringActorSystem.class);

        // Get the artery ports for each node from the cluster configuration
        int workerPort = (Integer)
                systemWorker.getCluster().selfMember().address().port().get();
        int coordinatorPort = (Integer)
                systemCoordinator.getCluster().selfMember().address().port().get();

        // Test WorkerRoleShardedActor - create multiple entities with different IDs
        // This ensures entities are distributed across different shards, but all still
        // end up on the node with "worker" role
        for (int i = 1; i <= TEST_ENTITY_COUNT; i++) {
            String entityId = "worker-" + i;
            SpringShardedActorHandle<WorkerRoleShardedActor.Command> workerRef = systemSeed
                    .sharded(WorkerRoleShardedActor.class)
                    .withId(entityId)
                    .get();

            NodeInfo workerNodeInfo = workerRef
                    .ask(new WorkerRoleShardedActor.GetNodeInfo())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Verify each worker entity is running on the node with "worker" role
            assertEquals(
                    workerPort,
                    workerNodeInfo.getPort(),
                    "WorkerRoleShardedActor " + entityId + " should be created on node with 'worker' role");
            assertTrue(
                    workerNodeInfo.getActorPath().contains("127.0.0.1:" + workerPort),
                    "Actor address for " + entityId + " should contain the worker node's port");
        }

        // Test CoordinatorRoleShardedActor - create multiple entities with different IDs
        // This ensures entities are distributed across different shards, but all still
        // end up on the node with "coordinator" role
        for (int i = 1; i <= TEST_ENTITY_COUNT; i++) {
            String entityId = "coord-" + i;
            SpringShardedActorHandle<CoordinatorRoleShardedActor.Command> coordRef = systemSeed
                    .sharded(CoordinatorRoleShardedActor.class)
                    .withId(entityId)
                    .get();

            NodeInfo coordNodeInfo = coordRef.ask(new CoordinatorRoleShardedActor.GetNodeInfo())
                    .withTimeout(Duration.ofSeconds(5))
                    .execute()
                    .toCompletableFuture()
                    .get();

            // Verify each coordinator entity is running on the node with "coordinator" role
            assertEquals(
                    coordinatorPort,
                    coordNodeInfo.getPort(),
                    "CoordinatorRoleShardedActor " + entityId + " should be created on node with 'coordinator' role");
            assertTrue(
                    coordNodeInfo.getActorPath().contains("127.0.0.1:" + coordinatorPort),
                    "Actor address for " + entityId + " should contain the coordinator node's port");
        }
    }
}
