package io.github.seonwkim.example.kotlin

import io.github.seonwkim.actor.AskCommand
import io.github.seonwkim.actor.PostStop
import io.github.seonwkim.actor.PreRestart
import io.github.seonwkim.actor.SpringActor
import io.github.seonwkim.actor.SpringActorBehavior
import io.github.seonwkim.actor.SpringActorContext
import io.github.seonwkim.actor.SpringBehaviorContext
import io.github.seonwkim.actor.SupervisionStrategy
import io.github.seonwkim.actor.kotlin.actorBehavior
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Answers `SayHello`, restarts on `TriggerFailure`, and logs its lifecycle signals. Each actor id
 * gets its own instance and state.
 */
@Component
class HelloActor : SpringActor<HelloActor.Command> {
    sealed interface Command

    /** Asks for a greeting. */
    class SayHello : AskCommand<String>(), Command

    /** Replies, then fails, so the actor restarts (see the PreRestart handler). */
    class TriggerFailure : AskCommand<String>(), Command

    override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> =
        actorBehavior<Command>(actorContext)
            .withState { ctx ->
                ctx.log.info("PreStart hook for id={}", actorContext.actorId())
                HelloBehavior(ctx, actorContext.actorId())
            }.onMessage(SayHello::class.java) { state, msg -> state.onSayHello(msg) }
            .onMessage(TriggerFailure::class.java) { state, msg -> state.onTriggerFailure(msg) }
            .onSignal(PreRestart::class.java) { state, _ -> state.onPreRestart() }
            .onSignal(PostStop::class.java) { state, _ -> state.onPostStop() }
            .withSupervisionStrategy(SupervisionStrategy.restart().withLimit(10, Duration.ofMinutes(1)))
            .build()

    /** The actor's state and handlers, kept apart from the bean. */
    private class HelloBehavior(
        private val ctx: SpringBehaviorContext<Command>,
        private val actorId: String,
    ) {
        fun onSayHello(msg: SayHello): SpringActorBehavior<Command> {
            ctx.log.info("Received SayHello for id={}", actorId)
            msg.reply("Hello from actor $actorId")
            return SpringActorBehavior.same()
        }

        fun onTriggerFailure(msg: TriggerFailure): SpringActorBehavior<Command> {
            ctx.log.warn("Triggering failure for actor {}", actorId)
            msg.reply("Triggering failure - actor will restart")
            throw IllegalStateException("Intentional failure to demonstrate PreRestart signal")
        }

        fun onPreRestart(): SpringActorBehavior<Command> {
            ctx.log.warn("Actor {} is being restarted due to failure", actorId)
            return SpringActorBehavior.same()
        }

        fun onPostStop(): SpringActorBehavior<Command> {
            ctx.log.info("Actor {} is stopping. Performing cleanup...", actorId)
            return SpringActorBehavior.same()
        }
    }
}
