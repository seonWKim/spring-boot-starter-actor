package io.github.seonwkim.actor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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

/** Event-sourced actors keep their state across application restarts, on the application's DataSource. */
class EventSourcedActorTest {

    @Test
    void anEventSourcedActorRecoversItsStateAfterARestart() throws Exception {
        assertRecoversAfterARestart(h2(UUID.randomUUID().toString()));
    }

    /** The starter's PostgreSQL script, run on both starts, and the PostgreSQL profile. */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class OnPostgres {
        @Container
        final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

        @Test
        void anEventSourcedActorRecoversItsStateAfterARestart() throws Exception {
            assertRecoversAfterARestart(createdOnEveryStart(database));
        }
    }

    /** The starter's MySQL script, run on both starts, and the MySQL profile. */
    @Nested
    @Testcontainers(disabledWithoutDocker = true)
    class OnMySql {
        @Container
        final MySQLContainer<?> database = new MySQLContainer<>("mysql:8.4");

        @Test
        void anEventSourcedActorRecoversItsStateAfterARestart() throws Exception {
            assertRecoversAfterARestart(createdOnEveryStart(database));
        }
    }

    private static void assertRecoversAfterARestart(String... properties) throws Exception {
        try (ConfigurableApplicationContext app = start(AccountActor.class, properties)) {
            SpringActorHandle<Command> account = spawn(app, AccountActor.class, "alice");
            assertThat(ask(account, new Deposit(5))).isEqualTo(5L);
            assertThat(ask(account, new Deposit(7))).isEqualTo(12L);
        }

        try (ConfigurableApplicationContext app = start(AccountActor.class, properties)) {
            SpringActorHandle<Command> account = spawn(app, AccountActor.class, "alice");
            assertThat(ask(account, new GetBalance())).isEqualTo(12L);
        }
    }

    @Test
    void recoveryStartsFromTheLatestSnapshot() throws Exception {
        String[] database = h2(UUID.randomUUID().toString());
        try (ConfigurableApplicationContext app = start(SnapshottingAccountActor.class, database)) {
            SpringActorHandle<Command> account = spawn(app, SnapshottingAccountActor.class, "bob");
            for (int i = 0; i < 12; i++) {
                ask(account, new Deposit(1));
            }
            // Snapshots are stored after the events: wait for the one at event 10 before stopping.
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            await().atMost(Duration.ofSeconds(10))
                    .until(
                            () -> jdbc.queryForObject("SELECT MAX(\"sequence_number\") FROM \"snapshot\"", Long.class),
                            sequenceNumber -> sequenceNumber != null && sequenceNumber == 10L);
        }

        try (ConfigurableApplicationContext app = start(SnapshottingAccountActor.class, database)) {
            SpringActorHandle<Command> account = spawn(app, SnapshottingAccountActor.class, "bob");
            assertThat(ask(account, new GetBalance())).isEqualTo(12L);
            // Without the snapshot, recovery would replay all 12 events.
            assertThat(app.getBean(SnapshottingAccountActor.class).eventsApplied.get())
                    .isEqualTo(2);
        }
    }

    @Test
    void aThrowingCommandHandlerFailsAFailFastAskAtOnce() {
        try (ConfigurableApplicationContext app =
                start(AccountActor.class, h2(UUID.randomUUID().toString()))) {
            SpringActorHandle<Command> account = spawn(app, AccountActor.class, "carol");

            // The ask's own timeout is far longer than the wait, so only a fast failure passes.
            assertThatThrownBy(() -> account.ask(new Overdraw())
                            .withTimeout(Duration.ofSeconds(60))
                            .failFastOnError()
                            .execute()
                            .toCompletableFuture()
                            .get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("insufficient funds");
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
            "spring.actor.persistence.initialize-schema=always"
        };
    }

    private static ConfigurableApplicationContext start(Class<?> actorClass, String... properties) {
        return new SpringApplicationBuilder(TestApp.class, actorClass)
                .web(WebApplicationType.NONE)
                .properties(properties)
                .run();
    }

    private static SpringActorHandle<Command> spawn(
            ConfigurableApplicationContext app, Class<? extends SpringActor<Command>> actorClass, String id) {
        return app.getBean(SpringActorSystem.class).actor(actorClass).withId(id).spawnAndWait();
    }

    private static long ask(SpringActorHandle<Command> account, AskCommand<Long> command) throws Exception {
        return account.ask(command)
                .withTimeout(Duration.ofSeconds(10))
                .execute()
                .toCompletableFuture()
                .get(15, TimeUnit.SECONDS);
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

    static final class GetBalance extends AskCommand<Long> implements Command {}

    /** Its handler always throws. */
    static final class Overdraw extends AskCommand<Long> implements Command {}

    interface Event extends JsonSerializable {}

    record Deposited(long amount) implements Event {}

    record Account(long balance) implements JsonSerializable {
        Account add(long amount) {
            return new Account(balance + amount);
        }
    }

    static class AccountActor implements SpringActor<Command> {

        final AtomicInteger eventsApplied = new AtomicInteger();

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return configure(SpringEventSourcedBehavior.builder(
                                    Command.class,
                                    Event.class,
                                    PersistenceId.of(getClass().getSimpleName(), actorContext.actorId()),
                                    new Account(0))
                            .onCommand(Deposit.class, (ctx, account, cmd) -> ctx.persist(new Deposited(cmd.amount))
                                    .thenReply(cmd, Account::balance))
                            .onCommand(GetBalance.class, (ctx, account, cmd) -> ctx.none()
                                    .thenReply(cmd, Account::balance))
                            .onCommand(Overdraw.class, (ctx, account, cmd) -> {
                                throw new IllegalStateException("insufficient funds");
                            })
                            .onEvent(Deposited.class, (account, event) -> {
                                eventsApplied.incrementAndGet();
                                return account.add(event.amount());
                            }))
                    .build();
        }

        SpringEventSourcedBehavior.Builder<Command, Event, Account> configure(
                SpringEventSourcedBehavior.Builder<Command, Event, Account> builder) {
            return builder;
        }
    }

    static class SnapshottingAccountActor extends AccountActor {
        @Override
        SpringEventSourcedBehavior.Builder<Command, Event, Account> configure(
                SpringEventSourcedBehavior.Builder<Command, Event, Account> builder) {
            return builder.snapshotEvery(5);
        }
    }
}
