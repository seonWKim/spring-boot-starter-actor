package io.github.seonwkim.example.kotlin

import io.github.seonwkim.actor.SpringActorSystem
import io.github.seonwkim.actor.kotlin.awaitAsk
import io.github.seonwkim.actor.kotlin.awaitGetOrSpawn
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

/** Talks to the hello actor with suspend functions instead of CompletionStage chains. */
@Service
class HelloService(
    private val actorSystem: SpringActorSystem,
) {
    suspend fun hello(): String = helloActor().awaitAsk(HelloActor.SayHello(), timeout = 3.seconds)

    suspend fun triggerRestart(): String = helloActor().awaitAsk(HelloActor.TriggerFailure(), timeout = 3.seconds)

    suspend fun stopActor(): String {
        helloActor().stop()
        return "Actor stopped - PostStop signal will be triggered"
    }

    // The reified helper replaces getOrSpawn(HelloActor::class.java, ...): `_` lets Kotlin infer the command type.
    private suspend fun helloActor() = actorSystem.awaitGetOrSpawn<HelloActor, _>(ACTOR_ID)

    private companion object {
        const val ACTOR_ID = "hello-actor"
    }
}
