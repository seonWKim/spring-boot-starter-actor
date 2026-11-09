package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.SpringActorHandle
import io.github.seonwkim.actor.SpringActorSpawnBuilder
import io.github.seonwkim.actor.SpringActorSystem
import io.github.seonwkim.actor.SpringActorWithContext
import io.github.seonwkim.actor.shard.SpringShardedActor
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.toJavaDuration

// Kotlin can't infer the command type C from the actor type alone, so pass `_` for it:
// `actorSystem.actor<HelloActor, _>()`.

/** [SpringActorSystem.actor] with a reified actor type: `actorSystem.actor<HelloActor, _>()`. */
inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorSystem.actor(): SpringActorSpawnBuilder<A, C> = actor(A::class.java)

/** [SpringActorSystem.sharded] with a reified actor type: `actorSystem.sharded<CounterActor, _>()`. */
inline fun <reified A : SpringShardedActor<T>, T> SpringActorSystem.sharded(): SpringShardedActorBuilder<T> = sharded(A::class.java)

/** Suspends until the actor with this id runs, spawning it when none does ([SpringActorSystem.getOrSpawn]). */
suspend inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorSystem.awaitGetOrSpawn(actorId: String): SpringActorHandle<C> =
    getOrSpawn(A::class.java, actorId).await()

/** Suspends until the lookup completes: the running actor with this id, or null when none runs ([SpringActorSystem.get]). */
suspend inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorSystem.awaitGet(actorId: String): SpringActorHandle<C>? =
    get(A::class.java, actorId).await()

/** Suspends until the check completes: whether an actor with this id runs ([SpringActorSystem.exists]). */
suspend inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorSystem.awaitExists(actorId: String): Boolean =
    exists(A::class.java, actorId).await()

/** [SpringActorSystem.unwrap] with a reified type. */
inline fun <reified X> SpringActorSystem.unwrap(): X = unwrap(X::class.java)

/** [SpringActorSpawnBuilder.withTimeout] with a Kotlin [Duration]. */
fun <A : SpringActorWithContext<C, *>, C> SpringActorSpawnBuilder<A, C>.withTimeout(timeout: Duration): SpringActorSpawnBuilder<A, C> =
    withTimeout(timeout.toJavaDuration())

/** Spawns the actor and suspends until it runs ([SpringActorSpawnBuilder.spawn]). */
suspend fun <A : SpringActorWithContext<C, *>, C> SpringActorSpawnBuilder<A, C>.awaitSpawn(): SpringActorHandle<C> = spawn().await()

/** Suspends until the actor with this builder's id runs, spawning it when none does ([SpringActorSpawnBuilder.getOrSpawn]). */
suspend fun <A : SpringActorWithContext<C, *>, C> SpringActorSpawnBuilder<A, C>.awaitGetOrSpawn(): SpringActorHandle<C> =
    getOrSpawn().await()

/** [SpringShardedActorBuilder.withTimeout] with a Kotlin [Duration]. */
fun <T> SpringShardedActorBuilder<T>.withTimeout(timeout: Duration): SpringShardedActorBuilder<T> = withTimeout(timeout.toJavaDuration())
