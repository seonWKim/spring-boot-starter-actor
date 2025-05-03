package org.github.seonwkim.core.service;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.github.seonwkim.core.ActorRefWrapper;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior.ChildCreated;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior.Command;
import org.github.seonwkim.core.behaviors.impl.ActorCreationBehavior.CreateChild;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ActorLocalService {

    private final GenericApplicationContext genericApplicationContext;
    private final ActorSystem actorSystem;
    // lazily initialized
    private ActorRefWrapper<Command> actorCreationBehavior;

    public ActorLocalService(GenericApplicationContext genericApplicationContext, ActorSystem actorSystem) {
        this.genericApplicationContext = genericApplicationContext;
        this.actorSystem = actorSystem;
    }

    @SuppressWarnings("unchcked")
    public <T> ActorRefWrapper<T> getActorRefWrapper(String beanName, Class<?> clazz) {
        return (ActorRefWrapper<T>) genericApplicationContext.getBean(beanName, clazz);
    }

    public <T> CompletionStage<ActorRef<T>> createLocalActor(
            String childName,
            Supplier<Behavior<T>> behaviorSupplier,
            Duration timeout) {
        return AskPattern.<ActorCreationBehavior.Command, ChildCreated<T>>ask(
                actorCreationBehavior.unwrap(),
                replyTo -> new CreateChild<>(childName, behaviorSupplier, replyTo),
                timeout,
                actorSystem.scheduler()
        ).thenApply(ChildCreated::getChildRef);
    }

    public <T> void tell(ActorRef<T> actorRef, T message) {
        actorRef.tell(message);
    }

    public <T, Q> CompletionStage<Q> ask(ActorRef<T> actorRef, Function<ActorRef<Q>, T> messageFactory,
                                         Duration timeout) {
        return AskPattern.ask(
                actorRef,
                messageFactory::apply,
                timeout,
                actorSystem.scheduler()
        );
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initializeActorCreationBehavior() {
        actorCreationBehavior = getActorRefWrapper(ActorCreationBehavior.BEAN_NAME, ActorRefWrapper.class);
    }
}
