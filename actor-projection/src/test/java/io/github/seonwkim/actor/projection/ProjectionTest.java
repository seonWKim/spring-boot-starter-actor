package io.github.seonwkim.actor.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.persistence.PersistenceId;
import io.github.seonwkim.actor.persistence.SpringEventSourcedBehavior;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Projections write events into the application's database and resume from their stored offset. */
class ProjectionTest {

    @Test
    void anExactlyOnceProjectionResumesFromItsStoredOffsetAfterARestart() throws Exception {
        assertExactlyOnceResumesAfterARestart(h2(UUID.randomUUID().toString()));
    }

    /** The PostgreSQL offset tables, created on both starts, and the postgres dialect. */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class OnPostgres {
        @Container
        final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

        @Test
        void anExactlyOnceProjectionResumesFromItsStoredOffsetAfterARestart() throws Exception {
            assertExactlyOnceResumesAfterARestart(createdOnEveryStart(database));
        }
    }

    /** The MySQL offset tables, created on both starts, and the mysql dialect. */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class OnMySql {
        @Container
        final MySQLContainer<?> database = new MySQLContainer<>("mysql:8.4");

        @Test
        void anExactlyOnceProjectionResumesFromItsStoredOffsetAfterARestart() throws Exception {
            assertExactlyOnceResumesAfterARestart(createdOnEveryStart(database));
        }
    }

    private static void assertExactlyOnceResumesAfterARestart(String... database) throws Exception {
        try (ConfigurableApplicationContext app = start(ExactlyOnceDeposits.class, database)) {
            deposit(app, 3);
            awaitDeposits(app, 3);
        }

        try (ConfigurableApplicationContext app = start(ExactlyOnceDeposits.class, database)) {
            deposit(app, 2);
            awaitDeposits(app, 5);
            // From the start it would handle all 5, and fail on the rows already there.
            assertThat(app.getBean(ExactlyOnceDeposits.class).handled.get()).isEqualTo(2);
        }
    }

    @Test
    void anAtLeastOnceProjectionResumesFromItsStoredOffsetAfterARestart() throws Exception {
        String[] database = h2(UUID.randomUUID().toString());
        try (ConfigurableApplicationContext app = start(AtLeastOnceDeposits.class, database)) {
            deposit(app, 3);
            awaitDeposits(app, 3);
            // At least once stores the offset after the handler, in batches: wait for it before stopping.
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForList(
                            "SELECT \"current_offset\" FROM \"pekko_projection_offset_store\"", String.class)
                    .contains("3"));
        }

        try (ConfigurableApplicationContext app = start(AtLeastOnceDeposits.class, database)) {
            deposit(app, 2);
            awaitDeposits(app, 5);
            assertThat(app.getBean(AtLeastOnceDeposits.class).handled.get()).isEqualTo(2);
        }
    }

    @Test
    void aFailedEventRollsBackItsWritesAndIsHandledAgain() throws Exception {
        try (ConfigurableApplicationContext app =
                start(FailingOnceDeposits.class, h2(UUID.randomUUID().toString()))) {
            deposit(app, 3);
            // The failed attempt's row is rolled back: had it been committed, the retry would hit the
            // primary key and the third row would never come.
            awaitDeposits(app, 3);
            assertThat(app.getBean(FailingOnceDeposits.class).handled.get()).isEqualTo(4);
        }
    }

    @Test
    void inClusterModeAProjectionRunsAsAClusterSingleton() throws Exception {
        String[] oneNodeCluster = {
            "spring.actor.provider=cluster", "spring.actor.remote.port=0", "spring.actor.cluster.join-self=true"
        };
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(
                        TestApp.class, AccountActor.class, ExactlyOnceDeposits.class)
                .web(WebApplicationType.NONE)
                .properties(h2(UUID.randomUUID().toString()))
                .properties(oneNodeCluster)
                .properties("spring.sql.init.schema-locations=classpath:deposits-schema.sql")
                .run()) {
            deposit(app, 3);
            awaitDeposits(app, 3);
        }
    }

    private static String[] h2(String database) {
        return new String[] {"spring.datasource.url=jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1"};
    }

    private static String[] createdOnEveryStart(JdbcDatabaseContainer<?> database) {
        return new String[] {
            "spring.datasource.url=" + database.getJdbcUrl(),
            "spring.datasource.username=" + database.getUsername(),
            "spring.datasource.password=" + database.getPassword(),
            "spring.sql.init.mode=always",
            "spring.actor.persistence.initialize-schema=always"
        };
    }

    private static ConfigurableApplicationContext start(Class<?> projectionClass, String... database) {
        return new SpringApplicationBuilder(TestApp.class, AccountActor.class, projectionClass)
                .web(WebApplicationType.NONE)
                .properties(database)
                .properties(
                        "spring.sql.init.schema-locations=classpath:deposits-schema.sql",
                        // Tests read new events and retry failures quickly.
                        "spring.actor.pekko.projection.restart-backoff.min-backoff=100ms",
                        "spring.actor.pekko.projection.restart-backoff.max-backoff=500ms",
                        "spring.actor.jdbc-read-journal.refresh-interval=100ms")
                .run();
    }

    private static void deposit(ConfigurableApplicationContext app, int times) throws Exception {
        SpringActorHandle<Command> account = app.getBean(SpringActorSystem.class)
                .actor(AccountActor.class)
                .withId("alice")
                .spawnAndWait();
        for (int i = 0; i < times; i++) {
            account.ask(new Deposit(10))
                    .withTimeout(Duration.ofSeconds(10))
                    .execute()
                    .toCompletableFuture()
                    .get(15, TimeUnit.SECONDS);
        }
    }

    private static void awaitDeposits(ConfigurableApplicationContext app, int rows) {
        JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
        await().atMost(Duration.ofSeconds(20))
                .until(
                        () -> jdbc.queryForObject("SELECT COUNT(*) FROM deposits", Integer.class),
                        count -> count == rows);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableActorSupport
    static class TestApp {}

    interface Command {}

    static final class Deposit extends AskCommand<Long> implements Command {
        final long amount;

        Deposit(long amount) {
            this.amount = amount;
        }
    }

    interface Event extends JsonSerializable {}

    record Deposited(long amount) implements Event {}

    record Account(long balance) implements JsonSerializable {}

    static class AccountActor implements SpringActor<Command> {
        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringEventSourcedBehavior.builder(
                            Command.class,
                            Event.class,
                            PersistenceId.of("Account", actorContext.actorId()),
                            new Account(0))
                    .onCommand(Deposit.class, (ctx, account, cmd) -> ctx.persist(new Deposited(cmd.amount))
                            .thenReply(cmd, Account::balance))
                    .onEvent(Deposited.class, (account, event) -> new Account(account.balance() + event.amount()))
                    .withTagger(event -> Set.of("accounts"))
                    .build();
        }
    }

    /** Inserts one row per event; a second insert of the same event fails on the primary key. */
    abstract static class Deposits implements SpringProjection<Event> {
        final AtomicInteger handled = new AtomicInteger();
        private final JdbcTemplate jdbc;

        Deposits(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String name() {
            return getClass().getSimpleName();
        }

        @Override
        public String tag() {
            return "accounts";
        }

        @Override
        public void handle(ProjectedEvent<Event> event) {
            handled.incrementAndGet();
            jdbc.update(
                    "INSERT INTO deposits (persistence_id, sequence_number, amount) VALUES (?, ?, ?)",
                    event.persistenceId(),
                    event.sequenceNumber(),
                    ((Deposited) event.event()).amount());
        }
    }

    static class ExactlyOnceDeposits extends Deposits {
        ExactlyOnceDeposits(JdbcTemplate jdbc) {
            super(jdbc);
        }
    }

    static class AtLeastOnceDeposits extends Deposits {
        AtLeastOnceDeposits(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public Delivery delivery() {
            return Delivery.AT_LEAST_ONCE;
        }
    }

    static class FailingOnceDeposits extends Deposits {
        private final AtomicInteger failures = new AtomicInteger();

        FailingOnceDeposits(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public void handle(ProjectedEvent<Event> event) {
            super.handle(event);
            if (event.sequenceNumber() == 2 && failures.getAndIncrement() == 0) {
                throw new IllegalStateException("fails once, after its insert");
            }
        }
    }
}
