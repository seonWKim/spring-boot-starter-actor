package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringActorRef
import io.github.seonwkim.actor.SpringBehaviorContext
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** [SpringActorBehavior.builder] with a reified command type: `actorBehavior<Command>(actorContext)`. */
inline fun <reified C> actorBehavior(actorContext: SpringActorContext): SpringActorBehavior.Builder<C, SpringBehaviorContext<C>> =
    SpringActorBehavior.builder(C::class.java, actorContext)

/** [SpringActorRef.unwrap] with a reified type. */
inline fun <reified X> SpringActorRef<*>.unwrap(): X = unwrap(X::class.java)

/** [SpringBehaviorContext.unwrap] with a reified type. */
inline fun <reified X> SpringBehaviorContext<*>.unwrap(): X = unwrap(X::class.java)

/** [SpringBehaviorContext.startSingleTimer] with a Kotlin [Duration]. */
fun <T : Any> SpringBehaviorContext<T>.startSingleTimer(
    key: Any,
    message: T,
    delay: Duration,
) = startSingleTimer(key, message, delay.toJavaDuration())

/** [SpringBehaviorContext.startTimerAtFixedRate] with a Kotlin [Duration]. */
fun <T : Any> SpringBehaviorContext<T>.startTimerAtFixedRate(
    key: Any,
    message: T,
    interval: Duration,
) = startTimerAtFixedRate(key, message, interval.toJavaDuration())
