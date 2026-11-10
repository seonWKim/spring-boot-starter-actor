package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.actor.internal.DefaultSpringActorContext;
import io.github.seonwkim.actor.internal.PekkoActorHandle;
import io.github.seonwkim.actor.internal.PekkoBehaviors;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** How an ask treats a handler that throws, and the command it is given (#189). */
class AskCommandHandlingTest {

    interface Command {}

    static class Echo extends AskCommand<String> implements Command {
        final String text;

        Echo(String text) {
            this.text = text;
        }
    }

    static class Fail extends AskCommand<String> implements Command {}

    /** Blocks the actor until released, so later messages queue up behind it. */
    record Hold(CountDownLatch release) implements Command {}

    private final ActorTestKit testKit = ActorTestKit.create();

    @AfterEach
    void tearDown() {
        testKit.shutdownTestKit();
    }

    private SpringActorHandle<Command> spawn() {
        SpringActorBehavior<Command> behavior = SpringActorBehavior.builder(
                        Command.class, new DefaultSpringActorContext("ask-" + UUID.randomUUID()))
                .onMessage(Echo.class, (ctx, msg) -> {
                    msg.reply(msg.text);
                    return SpringActorBehavior.same();
                })
                .onMessage(Fail.class, (ctx, msg) -> {
                    throw new IllegalStateException("boom");
                })
                .onMessage(Hold.class, (ctx, msg) -> {
                    try {
                        msg.release().await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return SpringActorBehavior.same();
                })
                .withSupervisionStrategy(SupervisionStrategy.restart())
                .build();
        return new PekkoActorHandle<>(testKit.system(), testKit.spawn(PekkoBehaviors.toPekko(behavior)));
    }

    @Test
    void failFastOnErrorFailsTheAskWithTheHandlersException() {
        SpringActorHandle<Command> actor = spawn();

        // The ask's own timeout is far longer than the wait below, so only a fast failure passes.
        CompletableFuture<String> reply = actor.ask(new Fail())
                .withTimeout(Duration.ofSeconds(30))
                .failFastOnError()
                .execute()
                .toCompletableFuture();

        assertThatThrownBy(() -> reply.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void withoutFailFastAThrowingHandlerLeavesTheAskToTimeOut() {
        SpringActorHandle<Command> actor = spawn();

        CompletableFuture<String> reply = actor.ask(new Fail())
                .withTimeout(Duration.ofMillis(300))
                .execute()
                .toCompletableFuture();

        assertThatThrownBy(() -> reply.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void oneCommandInstanceCanBeAskedTwiceAtOnce() throws Exception {
        SpringActorHandle<Command> actor = spawn();
        CountDownLatch release = new CountDownLatch(1);
        actor.tell(new Hold(release));

        // Both asks are queued behind Hold before the actor sees either, so each needs its own reply address.
        Echo echo = new Echo("hi");
        CompletableFuture<String> first =
                actor.ask(echo).withTimeout(Duration.ofSeconds(3)).execute().toCompletableFuture();
        CompletableFuture<String> second =
                actor.ask(echo).withTimeout(Duration.ofSeconds(3)).execute().toCompletableFuture();
        release.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("hi");
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("hi");
        assertThat(echo.hasReplyTo())
                .as("ask leaves the caller's command unchanged")
                .isFalse();
    }
}
