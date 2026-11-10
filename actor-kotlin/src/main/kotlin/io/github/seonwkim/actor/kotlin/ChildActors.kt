package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.SpringActorHandle
import io.github.seonwkim.actor.SpringActorWithContext
import io.github.seonwkim.actor.SpringChildActorBuilder
import io.github.seonwkim.actor.SpringChildActorReference
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.toJavaDuration

// As with actorSystem.actor<A, _>(), pass `_` for the child's command type: `parent.child<WorkerActor, _>()`.

/** [SpringActorHandle.child] with a reified child type: `parent.child<WorkerActor, _>()`. */
inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorHandle<*>.child(): SpringChildActorBuilder<*, C> = child(A::class.java)

/** [SpringActorHandle.child] (a lookup reference) with a reified child type: `parent.child<WorkerActor, _>("worker-1")`. */
inline fun <reified A : SpringActorWithContext<C, *>, C> SpringActorHandle<*>.child(childId: String): SpringChildActorReference<*, C> =
    child(A::class.java, childId)

/** [SpringChildActorBuilder.withTimeout] with a Kotlin [Duration]. */
fun <P, C> SpringChildActorBuilder<P, C>.withTimeout(timeout: Duration): SpringChildActorBuilder<P, C> =
    withTimeout(timeout.toJavaDuration())

/**
 * Spawns the child and suspends until it runs ([SpringChildActorBuilder.spawn]). Call it from outside the
 * parent; inside the parent's handlers, pass `spawn()` to `pipeToSelf` instead.
 */
suspend fun <C> SpringChildActorBuilder<*, C>.awaitSpawn(): SpringActorHandle<C> = spawn().await()

/** Suspends until the child runs, spawning it when none does ([SpringChildActorBuilder.getOrSpawn]). */
suspend fun <C> SpringChildActorBuilder<*, C>.awaitGetOrSpawn(): SpringActorHandle<C> = getOrSpawn().await()

/** Suspends until the lookup completes: the running child, or null when none runs ([SpringChildActorBuilder.get]). */
suspend fun <C> SpringChildActorBuilder<*, C>.awaitGet(): SpringActorHandle<C>? = get().await()

/** Suspends until the check completes: whether the child runs ([SpringChildActorBuilder.exists]). */
suspend fun SpringChildActorBuilder<*, *>.awaitExists(): Boolean = exists().await()

/** [SpringChildActorReference.withTimeout] with a Kotlin [Duration]. */
fun <P, C> SpringChildActorReference<P, C>.withTimeout(timeout: Duration): SpringChildActorReference<P, C> =
    withTimeout(timeout.toJavaDuration())

/** Suspends until the lookup completes: the running child, or null when none runs ([SpringChildActorReference.get]). */
suspend fun <C> SpringChildActorReference<*, C>.awaitGet(): SpringActorHandle<C>? = get().await().orElse(null)

/** Suspends until the check completes: whether the child runs ([SpringChildActorReference.exists]). */
suspend fun SpringChildActorReference<*, *>.awaitExists(): Boolean = exists().await()
