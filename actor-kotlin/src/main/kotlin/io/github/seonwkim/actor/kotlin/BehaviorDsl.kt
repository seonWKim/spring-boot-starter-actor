package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.ActorSignal
import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringBehaviorContext
import io.github.seonwkim.actor.SupervisionStrategy

/** Marks the behavior DSL: inside a nested `behavior { }`, the outer block's functions need an explicit receiver. */
@DslMarker
annotation class BehaviorDslMarker

/**
 * Builds a behavior with [SpringActorBehavior.builder]. Handlers run with the behavior context as their
 * receiver (`self`, `log`, timers) and return the next behavior:
 *
 * ```
 * override fun create(actorContext: SpringActorContext) =
 *     behavior<Command>(actorContext) {
 *         onMessage<Greet> { msg ->
 *             msg.reply("Hello, ${msg.name}!")
 *             same()
 *         }
 *     }
 * ```
 */
inline fun <reified C : Any> behavior(
    actorContext: SpringActorContext,
    block: BehaviorScope<C, SpringBehaviorContext<C>>.() -> Unit,
): SpringActorBehavior<C> = BehaviorScope(SpringActorBehavior.builder(C::class.java, actorContext)).apply(block).build()

/**
 * Builds a behavior whose handlers run with a state object as their receiver
 * ([SpringActorBehavior.Builder.withState]). [state] runs when the actor starts and on each restart:
 *
 * ```
 * behavior<Command, Counter>(actorContext, state = { ctx -> Counter(ctx) }) {
 *     onMessage<Add> { msg -> onAdd(msg) }
 * }
 * ```
 */
inline fun <reified C : Any, S> behavior(
    actorContext: SpringActorContext,
    noinline state: (SpringBehaviorContext<C>) -> S,
    block: BehaviorScope<C, S>.() -> Unit,
): SpringActorBehavior<C> = BehaviorScope(SpringActorBehavior.builder(C::class.java, actorContext).withState(state)).apply(block).build()

/** The body of a [behavior] block: handlers and settings, added to a [SpringActorBehavior.Builder]. */
@BehaviorDslMarker
class BehaviorScope<C : Any, S>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val builder: SpringActorBehavior.Builder<C, S>,
    ) {
        /** Handles messages of type [M] ([SpringActorBehavior.Builder.onMessage]). Handlers are tried in the order they were added. */
        inline fun <reified M : C> onMessage(crossinline handler: S.(M) -> SpringActorBehavior<C>) {
            builder.onMessage(M::class.java) { state, msg -> state.handler(msg) }
        }

        /** Handles the lifecycle signal [M]: `PreRestart`, `PostStop` or `ActorTerminated` ([SpringActorBehavior.Builder.onSignal]). */
        inline fun <reified M : ActorSignal> onSignal(crossinline handler: S.(M) -> SpringActorBehavior<C>) {
            builder.onSignal(M::class.java) { state, signal -> state.handler(signal) }
        }

        /** [SpringActorBehavior.Builder.withSupervisionStrategy]. */
        fun withSupervisionStrategy(strategy: SupervisionStrategy) {
            builder.withSupervisionStrategy(strategy)
        }

        /** Log context computed per message ([SpringActorBehavior.Builder.withMdc]). */
        fun withMdc(mdcForMessage: (C) -> Map<String, String>) {
            builder.withMdc(mdcForMessage)
        }

        /** Keep handling messages the same way ([SpringActorBehavior.same]). */
        fun same(): SpringActorBehavior<C> = SpringActorBehavior.same()

        /** Stop the actor after this message ([SpringActorBehavior.stopped]). */
        fun stopped(): SpringActorBehavior<C> = SpringActorBehavior.stopped()

        /** The handler didn't handle the message after all ([SpringActorBehavior.unhandled]). */
        fun unhandled(): SpringActorBehavior<C> = SpringActorBehavior.unhandled()

        @PublishedApi
        internal fun build(): SpringActorBehavior<C> = builder.build()
    }
