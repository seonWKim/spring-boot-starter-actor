package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.AskCommand
import io.github.seonwkim.actor.SpringActorHandle
import io.github.seonwkim.actor.shard.SpringShardedActorHandle
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Sends the command and suspends until the reply arrives.
 *
 * Throws [java.util.concurrent.TimeoutException] when no reply arrives within the timeout, unless
 * [SpringActorHandle.AskBuilder.onTimeout] supplied a fallback. Cancelling the coroutine stops the
 * wait; the command has already been sent.
 */
suspend fun <RES> SpringActorHandle.AskBuilder<RES>.await(): RES = execute().await()

/** [SpringActorHandle.AskBuilder.withTimeout] with a Kotlin [Duration]. */
fun <RES> SpringActorHandle.AskBuilder<RES>.withTimeout(timeout: Duration): SpringActorHandle.AskBuilder<RES> =
    withTimeout(timeout.toJavaDuration())

/**
 * Asks the actor and suspends until the reply arrives: `handle.ask(command).withTimeout(timeout).await()`.
 * Without [timeout], the handle's default timeout applies.
 *
 * @throws java.util.concurrent.TimeoutException when no reply arrives in time
 */
suspend fun <RES> SpringActorHandle<*>.awaitAsk(
    command: AskCommand<RES>,
    timeout: Duration? = null,
): RES = ask(command).withOptionalTimeout(timeout).await()

/**
 * Asks the sharded entity and suspends until the reply arrives. Without [timeout], the handle's
 * default timeout applies.
 *
 * @throws java.util.concurrent.TimeoutException when no reply arrives in time
 */
suspend fun <RES> SpringShardedActorHandle<*>.awaitAsk(
    command: AskCommand<RES>,
    timeout: Duration? = null,
): RES = ask(command).withOptionalTimeout(timeout).await()

private fun <RES> SpringActorHandle.AskBuilder<RES>.withOptionalTimeout(timeout: Duration?): SpringActorHandle.AskBuilder<RES> =
    if (timeout == null) this else withTimeout(timeout)
