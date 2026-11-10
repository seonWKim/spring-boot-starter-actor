package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.AskCommand
import io.github.seonwkim.actor.PostStop
import io.github.seonwkim.actor.SpringActor
import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringActorSystem
import io.github.seonwkim.actor.SupervisionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Handlers per message type, a stop, and a PostStop signal handler, written with the DSL. */
@Component
class GreeterDslActor : SpringActor<GreeterDslActor.Command> {
    sealed interface Command

    class Greet(
        val name: String,
    ) : AskCommand<String>(),
        Command

    class Shout(
        val text: String,
    ) : AskCommand<String>(),
        Command

    class Stop : Command

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        behavior<Command>(actorContext) {
            onMessage<Greet> { msg ->
                msg.reply("Hello, ${msg.name}!")
                same()
            }
            onMessage<Shout> { msg ->
                msg.reply(msg.text.uppercase())
                same()
            }
            onMessage<Stop> { stopped() }
            onSignal<PostStop> {
                STOPPED.add(actorContext.actorId())
                same()
            }
        }

    companion object {
        val STOPPED: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}

/** A state object as the handlers' receiver, kept across a failure by the resume strategy. */
@Component
class CounterDslActor : SpringActor<CounterDslActor.Command> {
    sealed interface Command

    class Add : Command

    class Fail : Command

    class Get : AskCommand<Int>(), Command

    class Counter {
        var value = 0
    }

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        behavior<Command, Counter>(actorContext, state = { Counter() }) {
            withSupervisionStrategy(SupervisionStrategy.resume())
            onMessage<Add> {
                value++
                same()
            }
            onMessage<Fail> { throw IllegalStateException("expected by the test") }
            onMessage<Get> { msg ->
                msg.reply(value)
                same()
            }
        }
}

@SpringBootTest(classes = [KotlinExtensionsTestApplication::class])
class BehaviorDslTest {
    @Autowired
    lateinit var actorSystem: SpringActorSystem

    private fun newId() = "dsl-" + UUID.randomUUID()

    @Test
    fun eachMessageRunsTheHandlerForItsType() =
        runBlocking<Unit> {
            val greeter = actorSystem.awaitGetOrSpawn<GreeterDslActor, _>(newId())

            assertThat(greeter.awaitAsk(GreeterDslActor.Greet("Kim"))).isEqualTo("Hello, Kim!")
            assertThat(greeter.awaitAsk(GreeterDslActor.Shout("hey"))).isEqualTo("HEY")
        }

    @Test
    fun stoppedStopsTheActorAndItsPostStopHandlerRuns() =
        runBlocking<Unit> {
            val id = newId()
            val greeter = actorSystem.awaitGetOrSpawn<GreeterDslActor, _>(id)

            greeter.tell(GreeterDslActor.Stop())

            await().untilAsserted {
                assertThat(GreeterDslActor.STOPPED).contains(id)
                assertThat(runBlocking { actorSystem.awaitExists<GreeterDslActor, _>(id) }).isFalse()
            }
        }

    @Test
    fun stateAndSupervisionStrategyApply() =
        runBlocking<Unit> {
            val counter = actorSystem.awaitGetOrSpawn<CounterDslActor, _>(newId())

            counter.tell(CounterDslActor.Add())
            counter.tell(CounterDslActor.Fail())
            counter.tell(CounterDslActor.Add())

            // Resumed, not restarted: the state object survives the failure.
            assertThat(counter.awaitAsk(CounterDslActor.Get())).isEqualTo(2)
        }
}
