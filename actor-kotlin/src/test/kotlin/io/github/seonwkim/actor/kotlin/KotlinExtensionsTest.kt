package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.AskCommand
import io.github.seonwkim.actor.EnableActorSupport
import io.github.seonwkim.actor.SpringActor
import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringActorSystem
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@SpringBootApplication
@EnableActorSupport
open class KotlinExtensionsTestApplication

@Component
class EchoActor : SpringActor<EchoActor.Command> {
    sealed interface Command

    class Echo(val text: String) : AskCommand<String>(), Command

    /** Never answered: the asker times out. */
    class Ignored : AskCommand<String>(), Command

    /** Its handler throws. */
    class Fail : AskCommand<String>(), Command

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        actorBehavior<Command>(actorContext)
            .onMessage(Echo::class.java) { _, msg ->
                msg.reply(msg.text)
                SpringActorBehavior.same()
            }.onMessage(Ignored::class.java) { _, _ -> SpringActorBehavior.same() }
            .onMessage(Fail::class.java) { _, _ -> throw IllegalStateException("boom") }
            .build()
}

@SpringBootTest(classes = [KotlinExtensionsTestApplication::class])
class KotlinExtensionsTest {
    @Autowired
    lateinit var actorSystem: SpringActorSystem

    private fun newId() = "echo-" + UUID.randomUUID()

    @Test
    fun awaitAskReturnsTheReply() =
        runBlocking<Unit> {
            val echo = actorSystem.awaitGetOrSpawn<EchoActor, _>(newId())

            assertThat(echo.awaitAsk(EchoActor.Echo("hello"), 3.seconds)).isEqualTo("hello")
            assertThat(echo.ask(EchoActor.Echo("builder")).withTimeout(3.seconds).await()).isEqualTo("builder")
        }

    @Test
    fun awaitAskThrowsTimeoutExceptionWhenNoReplyArrives() =
        runBlocking<Unit> {
            val echo = actorSystem.awaitGetOrSpawn<EchoActor, _>(newId())

            val waited =
                measureTime {
                    assertThrows<TimeoutException> { echo.awaitAsk(EchoActor.Ignored(), 200.milliseconds) }
                }

            // The given timeout applies, not the handle's 3-second default.
            assertThat(waited).isLessThan(2.seconds)
        }

    @Test
    fun awaitAskWithFailFastThrowsTheHandlersExceptionBeforeTheTimeout() =
        runBlocking<Unit> {
            val echo = actorSystem.awaitGetOrSpawn<EchoActor, _>(newId())

            val waited =
                measureTime {
                    val error = assertThrows<IllegalStateException> { echo.awaitAsk(EchoActor.Fail(), 30.seconds, failFast = true) }
                    assertThat(error).hasMessage("boom")
                }

            assertThat(waited).isLessThan(5.seconds)
        }

    @Test
    fun reifiedActorSpawnsTheSameActorAsTheClassArgument() =
        runBlocking<Unit> {
            val id = newId()

            val echo = actorSystem.actor<EchoActor, _>().withId(id).awaitSpawn()

            assertThat(echo.awaitAsk(EchoActor.Echo("spawned"))).isEqualTo("spawned")
            // The Class<T> counterpart finds the actor the reified helper spawned.
            assertThat(actorSystem.exists(EchoActor::class.java, id).toCompletableFuture().get()).isTrue()
        }

    @Test
    fun awaitGetReturnsNullForAMissingActorAndTheHandleForARunningOne() =
        runBlocking<Unit> {
            val id = newId()
            assertThat(actorSystem.awaitGet<EchoActor, _>(id)).isNull()

            actorSystem.actor(EchoActor::class.java).withId(id).spawn().toCompletableFuture().get()

            val found = actorSystem.awaitGet<EchoActor, _>(id)
            assertThat(found?.awaitAsk(EchoActor.Echo("found"))).isEqualTo("found")
        }
}
