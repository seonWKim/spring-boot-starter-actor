package org.github.seonwkim.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.github.seonwkim.core.behaviors.ShardBehavior;
import org.github.seonwkim.core.service.ActorClusterService;
import org.github.seonwkim.integration.PekkoClusterTest.CommonSimpleShardedBehavior.GetStateCommand;
import org.github.seonwkim.integration.PekkoClusterTest.CommonSimpleShardedBehavior.IncreaseCounter;
import org.github.seonwkim.integration.PekkoClusterTest.CommonSimpleShardedBehavior.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

public class PekkoClusterTest {

    private static ConfigurableApplicationContext context1;
    private static ConfigurableApplicationContext context2;
    private static ConfigurableApplicationContext context3;

    @PropertySource("classpath:application1.properties")
    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    static class Application1Config {
        @Component
        static class SimpleShardedBehavior extends CommonSimpleShardedBehavior {}
    }

    @PropertySource("classpath:application2.properties")
    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    static class Application2Config {
        @Component
        static class SimpleShardedBehavior extends CommonSimpleShardedBehavior {}
    }

    @PropertySource("classpath:application3.properties")
    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    static class Application3Config {
        @Component
        static class SimpleShardedBehavior extends CommonSimpleShardedBehavior {}
    }

    static class CommonSimpleShardedBehavior implements ShardBehavior<CommonSimpleShardedBehavior.Command> {

        interface Command extends Serializable {}

        public static class IncreaseCounter implements Command {
            public IncreaseCounter() {}
        }

        public static class GetStateCommand implements Command {
            private final ActorRef<State> replyTo;

            public GetStateCommand(ActorRef<State> replyTo) {
                this.replyTo = replyTo;
            }

            public ActorRef<State> getReplyTo() {
                return replyTo;
            }
        }

        public static class State implements Serializable {
            private final int messageCount;

            public State(int messageCount) {
                this.messageCount = messageCount;
            }

            public int getMessageCount() {
                return messageCount;
            }
        }

        private static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
                EntityTypeKey.create(Command.class, "SimpleShardedBehavior");

        @Override
        public EntityTypeKey<Command> getEntityTypeKey() {
            return ENTITY_TYPE_KEY;
        }

        @Override
        public Behavior<Command> create(EntityContext<Command> entityContext) {
            return Behaviors.setup(
                    context -> {
                        AtomicInteger messageCount = new AtomicInteger();
                        return Behaviors.receive(Command.class)
                                        .onMessage(IncreaseCounter.class, cmd -> {
                                            messageCount.incrementAndGet();
                                            context.getLog().info("Increasing counter");
                                            return Behaviors.same();
                                        })
                                        .onMessage(GetStateCommand.class, cmd -> {
                                            cmd.getReplyTo().tell(new State(messageCount.get()));
                                            return Behaviors.same();
                                        })
                                        .build();
                    }
            );
        }

        @Override
        public ShardingMessageExtractor<Command, Command> extractor() {
            return new ShardingMessageExtractor<Command, Command>() {
                private final int numberOfShards = 100;

                @Override
                public String entityId(Command message) {
                    return null;
                }

                @Override
                public String shardId(String entityId) {
                    return String.valueOf(entityId.hashCode() % numberOfShards);
                }

                @Override
                public Command unwrapMessage(Command message) {
                    return message;
                }
            };
        }
    }

    @BeforeAll
    public static void setup() {
        context1 = SpringApplication.run(Application1Config.class);
        context2 = SpringApplication.run(Application2Config.class);
        context3 = SpringApplication.run(Application3Config.class);
    }

    @Test
    public void testPekkoNameProperty() {
        String pekkoName1 = context1.getEnvironment().getProperty("pekko.name");
        String pekkoName2 = context2.getEnvironment().getProperty("pekko.name");
        String pekkoName3 = context3.getEnvironment().getProperty("pekko.name");

        assertEquals("cluster-node-1", pekkoName1);
        assertEquals("cluster-node-2", pekkoName2);
        assertEquals("cluster-node-3", pekkoName3);
    }

    @Test
    public void single_sharded_actor_across_cluster_test() throws Exception {
        // 1. Get ActorService from each context
        ActorClusterService actorService1 = context1.getBean(ActorClusterService.class);
        ActorClusterService actorService2 = context2.getBean(ActorClusterService.class);
        ActorClusterService actorService3 = context3.getBean(ActorClusterService.class);

        // 2. Get the same sharded actor from each ActorService
        EntityRef<CommonSimpleShardedBehavior.Command> shardedActor1 = actorService1.getShardedActor(
                CommonSimpleShardedBehavior.ENTITY_TYPE_KEY, "shared-entity");
        EntityRef<CommonSimpleShardedBehavior.Command> shardedActor2 = actorService2.getShardedActor(
                CommonSimpleShardedBehavior.ENTITY_TYPE_KEY, "shared-entity");
        EntityRef<CommonSimpleShardedBehavior.Command> shardedActor3 = actorService3.getShardedActor(
                CommonSimpleShardedBehavior.ENTITY_TYPE_KEY, "shared-entity");

        while (true) {
            final Set<Member> members = scala.collection.JavaConverters.setAsJavaSet(actorService1.getClusterState().members());
            if (members.size() != 3) {
                continue;
            }

            boolean allMembersUp = true;
            for (Member member : members) {
                if (!actorService1.getClusterState().isMemberUp(member.address())) {
                    allMembersUp = false;
                    break;
                }
            }
            if (allMembersUp) {
                break;
            }

            Thread.sleep(1000);
            System.out.println("Waiting for cluster to set up");
        }

        // 3. Send messages to the sharded actor from each ActorService
        shardedActor1.tell(new IncreaseCounter());
        shardedActor2.tell(new IncreaseCounter());
        shardedActor3.tell(new IncreaseCounter());

        // 4. Verify whether the sharded actor received all messages
        Thread.sleep(500); // wait for messages to be processed

        ActorSystem<?> actorSystem = context1.getBean(ActorSystem.class);
        CompletionStage<State> stateFuture = AskPattern.ask(
                shardedActor1,
                GetStateCommand::new,
                Duration.ofSeconds(3),
                actorSystem.scheduler()
        );

        CommonSimpleShardedBehavior.State state = stateFuture.toCompletableFuture().get();
        assertEquals(3, state.getMessageCount());
    }

    @AfterAll
    public static void tearDown() {
        context1.close();
        context2.close();
        context3.close();
    }
}
