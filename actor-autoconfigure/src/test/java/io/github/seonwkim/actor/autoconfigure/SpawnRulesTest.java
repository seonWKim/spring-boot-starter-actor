package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.FrameworkCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** What a spawn does when the id is taken, and when the actor needs a context an id can't make. */
@ExtendWith(OutputCaptureExtension.class)
class SpawnRulesTest {

    /** Counts Hits; extends FrameworkCommand so it can have children. */
    static class CounterActor implements SpringActor<CounterActor.Command> {
        public interface Command extends FrameworkCommand {}

        public record Hit() implements Command {}

        public static class Count extends AskCommand<Integer> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withState(c -> new int[1])
                    .onMessage(Hit.class, (count, msg) -> {
                        count[0]++;
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Count.class, (count, msg) -> {
                        msg.reply(count[0]);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    static class OrderContext extends SpringActorContext {
        final String orderId;

        OrderContext(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String actorId() {
            return orderId;
        }
    }

    /** Needs an {@link OrderContext}, which a spawn by id alone can't build. */
    static class OrderActor implements SpringActorWithContext<OrderActor.Command, OrderContext> {
        public interface Command {}

        @Override
        public SpringActorBehavior<Command> create(OrderContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx).build();
        }
    }

    @Configuration
    static class Beans {
        @Bean
        CounterActor counterActor() {
            return new CounterActor();
        }

        @Bean
        OrderActor orderActor() {
            return new OrderActor();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(ActorConfiguration.class, Beans.class));

    private static <T> T await(CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static int count(SpringActorHandle<CounterActor.Command> actor) throws Exception {
        return await(actor.ask(new CounterActor.Count())
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }

    @Test
    void spawningByIdAnActorThatNeedsACustomContextFailsNamingWithContext() {
        runner.run(context -> {
            SpringActorSystem system = context.getBean(SpringActorSystem.class);

            assertThatThrownBy(() -> await(system.getOrSpawn(OrderActor.class, "order-1")))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessageContaining(OrderContext.class.getName())
                    .hasMessageContaining("withContext(...)");

            // With the context it needs, the same actor spawns.
            assertThat(await(system.actor(OrderActor.class)
                            .withContext(new OrderContext("order-1"))
                            .getOrSpawn()))
                    .isNotNull();
        });
    }

    @Test
    void aChildSpawnOnATakenIdReturnsTheRunningChildAndWarnsThatItIsDeprecated(CapturedOutput output) {
        runner.run(context -> {
            SpringActorHandle<CounterActor.Command> parent = context.getBean(SpringActorSystem.class)
                    .actor(CounterActor.class)
                    .withId("parent")
                    .spawnAndWait();
            await(parent.child(CounterActor.class).withId("kid").spawn()).tell(new CounterActor.Hit());

            SpringActorHandle<CounterActor.Command> again =
                    await(parent.child(CounterActor.class).withId("kid").spawn());

            assertThat(count(again)).as("the running child, not a new one").isEqualTo(1);
            assertThat(output).contains("use getOrSpawn() to reuse a running child");
        });
    }

    @Test
    void aChildGetOrSpawnReturnsTheRunningChildWithoutAWarning(CapturedOutput output) {
        runner.run(context -> {
            SpringActorHandle<CounterActor.Command> parent = context.getBean(SpringActorSystem.class)
                    .actor(CounterActor.class)
                    .withId("parent")
                    .spawnAndWait();
            await(parent.child(CounterActor.class).withId("kid").getOrSpawn()).tell(new CounterActor.Hit());

            SpringActorHandle<CounterActor.Command> again =
                    await(parent.child(CounterActor.class).withId("kid").getOrSpawn());

            assertThat(count(again)).isEqualTo(1);
            assertThat(output).doesNotContain("use getOrSpawn() to reuse a running child");
        });
    }
}
