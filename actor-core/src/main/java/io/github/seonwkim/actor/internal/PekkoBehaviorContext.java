package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringBehaviorContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.slf4j.Logger;

/** A {@link SpringBehaviorContext} backed by a Pekko {@link ActorContext}. */
public final class PekkoBehaviorContext<T> implements SpringBehaviorContext<T> {

    private final ActorContext<T> ctx;

    public PekkoBehaviorContext(ActorContext<T> ctx) {
        this.ctx = ctx;
    }

    @Override
    public SpringActorHandle<T> getSelf() {
        return new PekkoActorHandle<>(ctx.getSystem().scheduler(), ctx.getSelf());
    }

    @Override
    public Logger getLog() {
        return ctx.getLog();
    }

    @Override
    public String path() {
        return ctx.getSelf().path().toString();
    }

    @Override
    public Optional<SpringActorRef<Void>> getChild(String name) {
        return ctx.getChild(name).map(PekkoActorRef::new);
    }

    @Override
    public List<SpringActorRef<Void>> getChildren() {
        return ctx.getChildren().stream()
                .<SpringActorRef<Void>>map(PekkoActorRef::new)
                .toList();
    }

    @Override
    public void stop(SpringActorRef<?> child) {
        ctx.stop(PekkoActorRef.toPekko(child));
    }

    @Override
    public <V> void pipeToSelf(CompletionStage<V> future, BiFunction<? super V, Throwable, ? extends T> toMessage) {
        ctx.pipeToSelf(future, (value, failure) -> toMessage.apply(value, failure));
    }

    @Override
    public void registerWithReceptionist(ServiceKey<T> key) {
        ctx.getSystem().receptionist().tell(Receptionist.register(key, ctx.getSelf()));
    }

    @Override
    public <X> X unwrap(Class<X> type) {
        if (type.isInstance(ctx)) {
            return type.cast(ctx);
        }
        throw new IllegalArgumentException("Cannot unwrap the behavior context to " + type.getName() + "; it wraps "
                + ActorContext.class.getName());
    }
}
