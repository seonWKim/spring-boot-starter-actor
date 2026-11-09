package io.github.seonwkim.actor.test.slice;

import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import org.springframework.stereotype.Component;

@Component
public class CounterActor implements SpringActor<CounterActor.Increment> {

    public record Increment() {}

    @Override
    public SpringActorBehavior<Increment> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Increment.class, actorContext)
                .onMessage(Increment.class, (ctx, msg) -> SpringActorBehavior.same())
                .build();
    }
}
