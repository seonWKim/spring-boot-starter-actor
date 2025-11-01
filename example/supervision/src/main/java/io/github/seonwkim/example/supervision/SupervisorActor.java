package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Supervisor actor that spawns and manages worker actors with different supervision strategies.
 * A supervisor cannot process work itself - it only manages child actors.
 */
@Component
public class SupervisorActor
        implements SpringActorWithContext<SupervisorActor, HierarchicalActor.Command, SpringActorContext> {

    private final LogPublisher logPublisher;

    public SupervisorActor(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @Override
    public Behavior<HierarchicalActor.Command> create(SpringActorContext actorContext) {
        return Behaviors.setup(ctx -> {
            HierarchicalActorBehavior<HierarchicalActor.Command> behavior =
                    new HierarchicalActorBehavior<>(ctx, actorContext, logPublisher, false, "Supervisor", WorkerActor.class);

            String actorId = actorContext.actorId();
            ctx.getLog().info("Supervisor {} started", actorId);
            logPublisher.publish(
                    String.format("[%s] Supervisor started (path: %s)", actorId, ctx.getSelf().path()));

            return Behaviors.receive(HierarchicalActor.Command.class)
                    .onMessage(HierarchicalActor.SpawnChild.class, behavior::onSpawnChild)
                    .onMessage(HierarchicalActor.RouteToChild.class, behavior::onRouteToChild)
                    .onMessage(HierarchicalActor.TriggerChildFailure.class, behavior::onTriggerChildFailure)
                    .onMessage(HierarchicalActor.StopChild.class, behavior::onStopChild)
                    .onMessage(HierarchicalActor.RouteSpawnChild.class, behavior::onRouteSpawnChild)
                    .onMessage(HierarchicalActor.GetHierarchy.class, behavior::onGetHierarchy)
                    .build();
        });
    }
}
