package io.github.seonwkim.core.instrumentation;

import org.apache.pekko.actor.ActorSystemImpl;
import org.apache.pekko.actor.Cell;
import org.apache.pekko.actor.InternalActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.dispatch.MessageDispatcher;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ActorCellInstrumentation {

    @Pointcut("execution(org.apache.pekko.actor.ActorCell.new(org.apache.pekko.actor.ActorSystemImpl, org.apache.pekko.actor.InternalActorRef, org.apache.pekko.actor.Props, org.apache.pekko.dispatch.MessageDispatcher, org.apache.pekko.actor.InternalActorRef)) && this(cell) && args(system, self, props, dispatcher, parent)")
    public void actorCellCreation(
            Cell cell,
            ActorSystemImpl system,
            InternalActorRef self,
            Props props,
            MessageDispatcher dispatcher,
            InternalActorRef parent
    ) {}

    @After("actorCellCreation(cell, system, self, props, dispatcher, parent)")
    public void afterCreation(Cell cell, ActorSystemImpl system, InternalActorRef self, Props props, MessageDispatcher dispatcher, InternalActorRef parent) {
        System.out.println("ActorCell created: " + cell.getClass().getName());
        // Add actual instrumentation logic here if needed
    }
}
