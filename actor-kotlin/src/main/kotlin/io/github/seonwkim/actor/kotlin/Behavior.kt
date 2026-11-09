package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringActorRef
import io.github.seonwkim.actor.SpringBehaviorContext

/** [SpringActorBehavior.builder] with a reified command type: `actorBehavior<Command>(actorContext)`. */
inline fun <reified C> actorBehavior(actorContext: SpringActorContext): SpringActorBehavior.Builder<C, SpringBehaviorContext<C>> =
    SpringActorBehavior.builder(C::class.java, actorContext)

/** [SpringActorRef.unwrap] with a reified type. */
inline fun <reified X> SpringActorRef<*>.unwrap(): X = unwrap(X::class.java)

/** [SpringBehaviorContext.unwrap] with a reified type. */
inline fun <reified X> SpringBehaviorContext<*>.unwrap(): X = unwrap(X::class.java)
