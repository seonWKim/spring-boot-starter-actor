package io.github.seonwkim.actor.test.slice;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import org.springframework.stereotype.Component;

@Component
public class GreeterActor implements SpringActor<GreeterActor.Greet> {

    public static class Greet extends AskCommand<String> {
        final String name;

        public Greet(String name) {
            this.name = name;
        }
    }

    @Override
    public SpringActorBehavior<Greet> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Greet.class, actorContext)
                .onMessage(Greet.class, (ctx, msg) -> {
                    msg.reply("hello " + msg.name);
                    return SpringActorBehavior.same();
                })
                .build();
    }
}
