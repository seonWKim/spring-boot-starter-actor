package io.github.seonwkim.actor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import io.github.seonwkim.actor.test.ActorCluster;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * An event-sourced actor as a sharded entity: one writer per id across the cluster, and its state
 * recovered from the journal on another node when its node leaves.
 */
class ShardedEventSourcedActorTest {

    @Test
    void anEntityRecoversItsEventsOnAnotherNodeAfterItsNodeLeaves() throws Exception {
        try (ActorCluster cluster = ActorCluster.builder(TestApp.class, AccountEntity.class)
                .nodes(3)
                .systemName("sharded-event-sourced")
                // One journal for every node, as with a shared database.
                .properties("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .start()) {
            // Commands from every node reach the one entity, which stores each event once.
            assertThat(ask(cluster, 1, new Deposit(5))).isEqualTo(5L);
            assertThat(ask(cluster, 2, new Deposit(7))).isEqualTo(12L);
            assertThat(ask(cluster, 3, new Deposit(1))).isEqualTo(13L);

            String host = ask(cluster, 1, new WhereAreYou());
            int hostNode = IntStream.rangeClosed(1, 3)
                    .filter(node -> address(cluster, node).equals(host))
                    .findFirst()
                    .orElseThrow();
            cluster.stop(hostNode);
            cluster.awaitMembersUp(2);

            int other = hostNode == 1 ? 2 : 1;
            // Asks during the hand-over time out; the entity then starts on a remaining node.
            await().atMost(Duration.ofSeconds(30))
                    .ignoreExceptions()
                    .until(() -> ask(cluster, other, new GetBalance()), balance -> balance == 13L);
            assertThat(ask(cluster, other, new WhereAreYou())).isNotEqualTo(host);
            assertThat(ask(cluster, other, new Deposit(1))).isEqualTo(14L);
        }
    }

    private static String address(ActorCluster cluster, int node) {
        return cluster.node(node).getBean(SpringActorSystem.class).nodeAddress();
    }

    private static <R> R ask(ActorCluster cluster, int node, AskCommand<R> command) throws Exception {
        return cluster.node(node)
                .getBean(SpringActorSystem.class)
                .sharded(AccountEntity.class)
                .withId("alice")
                .get()
                .ask(command)
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableActorSupport
    static class TestApp {}

    /** Commands cross nodes, so they are serializable, unlike in the local test. */
    public interface Command extends JsonSerializable {}

    public static final class Deposit extends AskCommand<Long> implements Command {
        public final long amount;

        public Deposit(long amount) {
            this.amount = amount;
        }
    }

    public static final class GetBalance extends AskCommand<Long> implements Command {}

    public static final class WhereAreYou extends AskCommand<String> implements Command {}

    public interface Event extends JsonSerializable {}

    public record Deposited(long amount) implements Event {}

    public record Account(long balance) implements JsonSerializable {
        Account add(long amount) {
            return new Account(balance + amount);
        }
    }

    public static class AccountEntity implements SpringShardedActor<Command> {

        static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "Account");

        @Override
        public ShardedEntityType<Command> entityType() {
            return TYPE;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            return SpringEventSourcedBehavior.builder(
                            Command.class, Event.class, PersistenceId.of("Account", ctx.getEntityId()), new Account(0))
                    .onCommand(Deposit.class, (es, account, cmd) -> es.persist(new Deposited(cmd.amount))
                            .thenReply(cmd, Account::balance))
                    .onCommand(GetBalance.class, (es, account, cmd) -> es.none().thenReply(cmd, Account::balance))
                    .onCommand(
                            WhereAreYou.class, (es, account, cmd) -> es.none().thenReply(cmd, state -> es.actorContext()
                                    .unwrap(ActorContext.class)
                                    .getSystem()
                                    .address()
                                    .toString()))
                    .onEvent(Deposited.class, (account, event) -> account.add(event.amount()))
                    .build();
        }
    }
}
