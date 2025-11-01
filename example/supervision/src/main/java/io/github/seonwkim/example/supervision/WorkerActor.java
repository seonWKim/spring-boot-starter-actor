package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.springframework.stereotype.Component;

/**
 * Worker actor that can process tasks, fail on command, and spawn its own children.
 * A worker can both perform work and supervise child actors.
 */
@Component
public class WorkerActor
        implements SpringActorWithContext<WorkerActor, HierarchicalActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public WorkerActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @Override
    public Behavior<HierarchicalActor.Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> {
            HierarchicalActorBehavior<HierarchicalActor.Command> behavior =
                    new HierarchicalActorBehavior<>(ctx, actorContext, logPublisher, true, "Worker", WorkerActor.class);

            String actorId = actorContext.actorId();
            ctx.getLog().info("Worker {} started", actorId);
            logPublisher.publish(
                    String.format("[%s] Worker started (path: %s)", actorId, ctx.getSelf().path()));

            return Behaviors.receive(HierarchicalActor.Command.class)
                    .onMessage(HierarchicalActor.ProcessWork.class, behavior::onProcessWork)
                    .onMessage(HierarchicalActor.TriggerFailure.class, behavior::onTriggerFailure)
                    .onMessage(HierarchicalActor.SpawnChild.class, behavior::onSpawnChild)
                    .onMessage(HierarchicalActor.RouteToChild.class, behavior::onRouteToChild)
                    .onMessage(HierarchicalActor.TriggerChildFailure.class, behavior::onTriggerChildFailure)
                    .onMessage(HierarchicalActor.StopChild.class, behavior::onStopChild)
                    .onMessage(HierarchicalActor.RouteSpawnChild.class, behavior::onRouteSpawnChild)
                    .onMessage(HierarchicalActor.GetHierarchy.class, behavior::onGetHierarchy)
                    .onSignal(PreRestart.class, behavior::onPreRestart)
                    .onSignal(PostStop.class, behavior::onPostStop)
                    .build();
        });
    }
}
