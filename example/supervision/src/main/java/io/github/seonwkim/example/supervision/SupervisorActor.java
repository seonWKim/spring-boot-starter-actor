package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import org.springframework.stereotype.Component;

/**
 * Supervisor actor that spawns and manages worker actors with different supervision strategies.
 * A supervisor cannot process work itself - it only manages child actors.
 */
@Component
public class SupervisorActor implements SpringActorWithContext<HierarchicalActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public SupervisorActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @Override
    public SpringActorBehavior<HierarchicalActor.Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(HierarchicalActor.Command.class, actorContext)
                .withState(ctx -> {
                    HierarchicalActorBehavior<HierarchicalActor.Command> behavior = new HierarchicalActorBehavior<>(
                            ctx, actorContext, logPublisher, false, "Supervisor", WorkerActor.class);

                    String actorId = actorContext.actorId();
                    ctx.getLog().info("Supervisor {} started", actorId);
                    logPublisher.publish(String.format(
                            "[%s] Supervisor started (path: %s)",
                            actorId, ctx.getSelf().path()));

                    return behavior;
                })
                .onMessage(HierarchicalActor.SpawnChild.class, HierarchicalActorBehavior::onSpawnChild)
                .onMessage(HierarchicalActor.RouteToChild.class, HierarchicalActorBehavior::onRouteToChild)
                .onMessage(
                        HierarchicalActor.TriggerChildFailure.class, HierarchicalActorBehavior::onTriggerChildFailure)
                .onMessage(HierarchicalActor.StopChild.class, HierarchicalActorBehavior::onStopChild)
                .onMessage(HierarchicalActor.RouteSpawnChild.class, HierarchicalActorBehavior::onRouteSpawnChild)
                .onMessage(HierarchicalActor.GetHierarchy.class, HierarchicalActorBehavior::onGetHierarchy)
                .build();
    }
}
