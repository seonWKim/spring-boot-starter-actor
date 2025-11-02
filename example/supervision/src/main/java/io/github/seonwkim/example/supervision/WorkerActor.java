package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.PreRestart;
import org.springframework.stereotype.Component;

/**
 * Worker actor that can process tasks, fail on command, and spawn its own children.
 * A worker can both perform work and supervise child actors.
 */
@Component
public class WorkerActor implements SpringActorWithContext<HierarchicalActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public WorkerActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @Override
    public SpringActorBehavior<HierarchicalActor.Command> create(SpringActorContext actorContext) {
        // Use array holder to capture behaviorHandler from onCreate callback
        return SpringActorBehavior.builder(HierarchicalActor.Command.class, actorContext)
                .onCreate(ctx -> {
                    HierarchicalActorBehavior<HierarchicalActor.Command> behavior = new HierarchicalActorBehavior<>(
                            ctx, actorContext, logPublisher, true, "Worker", WorkerActor.class);

                    String actorId = actorContext.actorId();
                    ctx.getLog().info("Worker {} started", actorId);
                    logPublisher.publish(String.format(
                            "[%s] Worker started (path: %s)",
                            actorId, ctx.getSelf().path()));

                    return behavior;
                })
                .onMessage(HierarchicalActor.ProcessWork.class, HierarchicalActorBehavior::onProcessWork)
                .onMessage(HierarchicalActor.TriggerFailure.class, HierarchicalActorBehavior::onTriggerFailure)
                .onMessage(HierarchicalActor.SpawnChild.class, HierarchicalActorBehavior::onSpawnChild)
                .onMessage(HierarchicalActor.RouteToChild.class, HierarchicalActorBehavior::onRouteToChild)
                .onMessage(HierarchicalActor.TriggerChildFailure.class, HierarchicalActorBehavior::onTriggerChildFailure)
                .onMessage(HierarchicalActor.StopChild.class, HierarchicalActorBehavior::onStopChild)
                .onMessage(HierarchicalActor.RouteSpawnChild.class, HierarchicalActorBehavior::onRouteSpawnChild)
                .onMessage(HierarchicalActor.GetHierarchy.class, HierarchicalActorBehavior::onGetHierarchy)
                .onSignal(PreRestart.class, HierarchicalActorBehavior::onPreRestart)
                .onSignal(PostStop.class, HierarchicalActorBehavior::onPostStop)
                .build();
    }
}
