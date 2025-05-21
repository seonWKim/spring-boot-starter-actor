package io.github.seonwkim.metrics;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

public class TestActorSystem {

    private final ActorSystem<Guardian.Command> actorSystem;

    public TestActorSystem() {
        this.actorSystem = ActorSystem.create(Guardian.create(), "testGuardian");
    }

    public ActorSystem<Guardian.Command> getActorSystem() {
        return actorSystem;
    }

    public <T> CompletionStage<ActorRef<T>> spawn(
            Class<T> commandClass, String actorId, Behavior<T> behavior, Duration timeout) {
        return AskPattern.ask(actorSystem,
                              (ActorRef<Guardian.Spawned<T>> replyTo) ->
                                      new Guardian.SpawnActor<>(commandClass, actorId, behavior, replyTo),
                              timeout,
                              actorSystem.scheduler())
                         .thenApply(spawned -> spawned.ref);
    }

    public static class Guardian {
        public interface Command {}

        public static class SpawnActor<T> implements Command {
            public final Class<T> commandClass;
            public final String actorId;
            public final Behavior<T> behavior;
            public final ActorRef<Spawned<T>> replyTo;

            public SpawnActor(
                    Class<T> commandClass,
                    String actorId,
                    Behavior<T> behavior,
                    ActorRef<Spawned<T>> replyTo) {
                this.commandClass = commandClass;
                this.actorId = actorId;
                this.behavior = behavior;
                this.replyTo = replyTo;
            }
        }

        public static class Spawned<T> {
            public final ActorRef<T> ref;

            public Spawned(ActorRef<T> ref) {
                this.ref = ref;
            }
        }

        public static Behavior<Command> create() {
            return Behaviors.setup(ctx ->
                                           Behaviors.receive(Command.class)
                                                    .onMessage(SpawnActor.class,
                                                               msg -> handleSpawnActor(ctx, msg))
                                                    .build());
        }

        private static <T> Behavior<Command> handleSpawnActor(
                org.apache.pekko.actor.typed.javadsl.ActorContext<Command> ctx,
                SpawnActor<T> msg) {
            ActorRef<T> actorRef = ctx.spawn(msg.behavior, msg.actorId);
            msg.replyTo.tell(new Spawned<>(actorRef));
            return Behaviors.same();
        }
    }
}
