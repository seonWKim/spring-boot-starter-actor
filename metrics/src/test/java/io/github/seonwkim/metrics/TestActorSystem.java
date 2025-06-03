package io.github.seonwkim.metrics;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

import io.github.seonwkim.metrics.TestActorSystem.Guardian.Spawned;

public class TestActorSystem {

	private final ActorSystem<Guardian.Command> actorSystem;

	public TestActorSystem() {
		this.actorSystem = ActorSystem.create(Guardian.create(), "testGuardian");
	}

	@SuppressWarnings("unchecked")
	public <A, C> CompletionStage<ActorRef<C>> spawn(
			Class<A> actorClass, String actorId, Behavior<C> behavior, Duration timeout) {
		return AskPattern.ask(
						actorSystem,
						(ActorRef<Guardian.Spawned<?>> replyTo) ->
								new Guardian.SpawnActor(actorClass, actorId, behavior, replyTo),
						timeout,
						actorSystem.scheduler())
				.thenApply(spawned -> (ActorRef<C>) spawned.ref);
	}

	public static class Guardian {
		public interface Command {}

		public static class SpawnActor implements Command {
			public final Class<?> actorClass;
			public final String actorId;
			public final Behavior<?> behavior;
			public final ActorRef<Spawned<?>> replyTo;

			public SpawnActor(
					Class<?> actorClass,
					String actorId,
					Behavior<?> behavior,
					ActorRef<Spawned<?>> replyTo) {
				this.actorClass = actorClass;
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
			return Behaviors.setup(
					ctx ->
							Behaviors.receive(Command.class)
									.onMessage(SpawnActor.class, msg -> handleSpawnActor(ctx, msg))
									.build());
		}

		private static Behavior<Command> handleSpawnActor(ActorContext<Command> ctx, SpawnActor msg) {
			ActorRef<?> actorRef = ctx.spawn(msg.behavior, msg.actorId);
			msg.replyTo.tell(new Spawned<>(actorRef));
			return Behaviors.same();
		}
	}
}
