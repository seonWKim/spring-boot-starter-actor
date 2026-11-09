package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorRef;
import org.apache.pekko.actor.typed.ActorRef;

/** A {@link SpringActorRef} backed by a Pekko {@link ActorRef}. Equal when the Pekko refs are. */
public class PekkoActorRef<T> implements SpringActorRef<T> {

    protected final ActorRef<T> ref;

    public PekkoActorRef(ActorRef<T> ref) {
        if (ref == null) {
            throw new IllegalArgumentException("actorRef must not be null");
        }
        this.ref = ref;
    }

    /** The Pekko ref behind any of the starter's references. */
    @SuppressWarnings("unchecked")
    public static <T> ActorRef<T> toPekko(SpringActorRef<T> ref) {
        return ref.unwrap(ActorRef.class);
    }

    @Override
    public void tell(T message) {
        ref.tell(message);
    }

    @Override
    public String path() {
        return ref.path().toString();
    }

    @Override
    public <X> X unwrap(Class<X> type) {
        if (type.isInstance(ref)) {
            return type.cast(ref);
        }
        throw new IllegalArgumentException("Cannot unwrap " + getClass().getSimpleName() + " to " + type.getName()
                + "; it wraps " + ActorRef.class.getName());
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof PekkoActorRef<?> other && ref.equals(other.ref);
    }

    @Override
    public final int hashCode() {
        return ref.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + path() + ")";
    }
}
