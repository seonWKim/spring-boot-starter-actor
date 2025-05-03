package org.github.seonwkim.core.behaviors.impl;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.behaviors.SingletonBehavior;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior.Command;
import org.springframework.stereotype.Component;

@Component
public class ActorCreationBehavior implements SingletonBehavior<Command> {

    public static final String BEAN_NAME = "system-actor-creation-behavior";

    public interface Command {}

    // Message to request a child actor creation
    public static class CreateChild<T> implements Command {
        private final String childName;
        private final Supplier<Behavior<T>> behaviorSupplier;
        private final ActorRef<ChildCreated<T>> replyTo;

        public CreateChild(String childName, Supplier<Behavior<T>> behaviorSupplier, ActorRef<ChildCreated<T>> replyTo) {
            this.childName = childName;
            this.behaviorSupplier = behaviorSupplier;
            this.replyTo = replyTo;
        }

        public String getChildName() {
            return childName;
        }

        public Supplier<Behavior<T>> getBehaviorSupplier() {
            return behaviorSupplier;
        }

        public ActorRef<ChildCreated<T>> getReplyTo() {
            return replyTo;
        }
    }

    // Message containing the created child actor reference
    public static class ChildCreated<T> {
        public final ActorRef<T> childRef;

        public ChildCreated(ActorRef<T> childRef) {
            this.childRef = childRef;
        }

        public ActorRef<T> getChildRef() {
            return childRef;
        }
    }

    public static class AskActor<T> implements Command {
        private final Consumer<ActorContext<T>> actorContextConsumer;

        public AskActor(Consumer<ActorContext<T>> actorContextConsumer) {
            this.actorContextConsumer = actorContextConsumer;
        }

        public Consumer<ActorContext<T>> getActorContextConsumer() {
            return actorContextConsumer;
        }
    }

    @Override
    public String beanName() {
        return BEAN_NAME;
    }

    /** The behavior that listens for child actor creation requests. */
    public Behavior<Command> create() {
        return Behaviors.setup(
                context ->
                        Behaviors.receive(Command.class)
                                 .onMessage(
                                         CreateChild.class,
                                         cmd -> {
                                             CreateChild<?> message = (CreateChild<?>) cmd;
                                             ActorRef<?> childRef = context.spawn(message.getBehaviorSupplier().get(), message.getChildName());
                                             ChildCreated childCreated = new ChildCreated(childRef);
                                             message.getReplyTo().tell(childCreated);
                                             return Behaviors.same();
                                         })
                                 .onMessage(AskActor.class,
                                            cmd -> {
                                                cmd.getActorContextConsumer().accept(context);
                                                return Behaviors.same();
                                            })
                                 .build());
    }
}
