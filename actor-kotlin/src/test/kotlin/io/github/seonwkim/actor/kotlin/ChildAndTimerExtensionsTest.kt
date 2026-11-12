package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.AskCommand
import io.github.seonwkim.actor.FrameworkCommand
import io.github.seonwkim.actor.SpringActor
import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringActorSystem
import io.github.seonwkim.actor.SpringBehaviorContext
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A parent with child operations turned on; its children are [EchoActor]s. */
@Component
class ParentActor : SpringActor<ParentActor.Command> {
    sealed interface Command : FrameworkCommand

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> = actorBehavior<Command>(actorContext).build()
}

/** Starts a single timer and a fixed-rate timer with Kotlin durations when asked. */
@Component
class TimerActor : SpringActor<TimerActor.Command> {
    sealed interface Command

    class Start : Command

    data object Once : Command

    data object Tick : Command

    class Get :
        AskCommand<Ticks>(),
        Command

    data class Ticks(
        val once: Int,
        val ticks: Int,
    )

    private class State(
        val ctx: SpringBehaviorContext<Command>,
    ) {
        var once = 0
        var ticks = 0
    }

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        actorBehavior<Command>(actorContext)
            .withState { ctx -> State(ctx) }
            .onMessage(Start::class.java) { state, _ ->
                state.ctx.startSingleTimer("once", Once, 20.milliseconds)
                state.ctx.startTimerAtFixedRate("tick", Tick, 20.milliseconds)
                SpringActorBehavior.same()
            }.onMessage(Once::class.java) { state, _ ->
                state.once++
                SpringActorBehavior.same()
            }.onMessage(Tick::class.java) { state, _ ->
                state.ticks++
                SpringActorBehavior.same()
            }.onMessage(Get::class.java) { state, msg ->
                msg.reply(Ticks(state.once, state.ticks))
                SpringActorBehavior.same()
            }.build()
}

@SpringBootTest(classes = [KotlinExtensionsTestApplication::class])
class ChildAndTimerExtensionsTest {
    @Autowired
    lateinit var actorSystem: SpringActorSystem

    private fun newId() = "kotlin-" + UUID.randomUUID()

    @Test
    fun childLookupsReturnNullForAMissingChildAndTheHandleForARunningOne() =
        runBlocking<Unit> {
            val parent = actorSystem.awaitGetOrSpawn<ParentActor, _>(newId())

            assertThat(parent.child<EchoActor, _>("child").awaitGet()).isNull()
            assertThat(parent.child<EchoActor, _>("child").awaitExists()).isFalse()

            parent
                .child<EchoActor, _>()
                .withId("child")
                .withTimeout(3.seconds)
                .awaitSpawn()

            val viaReference = parent.child<EchoActor, _>("child").withTimeout(3.seconds).awaitGet()
            assertThat(viaReference?.awaitAsk(EchoActor.Echo("reference"))).isEqualTo("reference")
            assertThat(parent.child<EchoActor, _>("child").awaitExists()).isTrue()
            assertThat(parent.child<EchoActor, _>().withId("child").awaitExists()).isTrue()
        }

    @Test
    fun childAwaitGetOrSpawnReturnsTheRunningChild() =
        runBlocking<Unit> {
            val parent = actorSystem.awaitGetOrSpawn<ParentActor, _>(newId())
            val spawned = parent.child<EchoActor, _>().withId("child").awaitGetOrSpawn()

            assertThat(parent.child<EchoActor, _>().withId("child").awaitGetOrSpawn()).isEqualTo(spawned)
        }

    @Test
    fun timersStartedWithKotlinDurationsFire() =
        runBlocking<Unit> {
            val timer = actorSystem.awaitGetOrSpawn<TimerActor, _>(newId())
            timer.tell(TimerActor.Start())

            await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted {
                val ticks = runBlocking { timer.awaitAsk(TimerActor.Get()) }
                assertThat(ticks.once).isEqualTo(1)
                assertThat(ticks.ticks).isGreaterThanOrEqualTo(3)
            }
        }
}
