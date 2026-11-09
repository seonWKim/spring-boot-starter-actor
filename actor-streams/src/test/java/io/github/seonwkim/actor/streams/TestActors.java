package io.github.seonwkim.actor.streams;

import io.github.seonwkim.actor.ActorTerminated;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringBehaviorContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Actors the bridge tests talk to. */
final class TestActors {

    private TestActors() {}

    /**
     * Doubles numbers, but holds every reply while the gate is closed: a slow actor whose speed the
     * test controls.
     */
    static class GateActor implements SpringActor<GateActor.Command> {
        interface Command {}

        static final class Double extends AskCommand<Integer> implements Command {
            final int value;

            Double(int value) {
                this.value = value;
            }
        }

        /** How many {@link Double}s the actor has received. */
        static final class Received extends AskCommand<Integer> implements Command {}

        record Open() implements Command {}

        private static final class State {
            boolean open;
            int received;
            final List<Double> held = new ArrayList<>();
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withState(c -> new State())
                    .onMessage(Double.class, (state, msg) -> {
                        state.received++;
                        if (state.open) {
                            msg.reply(msg.value * 2);
                        } else {
                            state.held.add(msg);
                        }
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Received.class, (state, msg) -> {
                        msg.reply(state.received);
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Open.class, (state, msg) -> {
                        state.open = true;
                        state.held.forEach(held -> held.reply(held.value * 2));
                        state.held.clear();
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    /** Sends what it is told to publish to every subscriber, and forgets a subscriber that stops. */
    static class TickerActor implements SpringActor<TickerActor.Command> {
        interface Command {}

        record Subscribe(SpringActorRef<String> subscriber) implements Command {}

        record Publish(String value) implements Command {}

        static final class Subscribers extends AskCommand<Integer> implements Command {}

        private record State(SpringBehaviorContext<Command> ctx, Set<SpringActorRef<?>> subscribers) {}

        @Override
        @SuppressWarnings("unchecked")
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withState(c -> new State(c, new HashSet<>()))
                    .onMessage(Subscribe.class, (state, msg) -> {
                        state.subscribers().add(msg.subscriber());
                        state.ctx().watch(msg.subscriber());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Publish.class, (state, msg) -> {
                        state.subscribers()
                                .forEach(subscriber -> ((SpringActorRef<String>) subscriber).tell(msg.value()));
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Subscribers.class, (state, msg) -> {
                        msg.reply(state.subscribers().size());
                        return SpringActorBehavior.same();
                    })
                    .onSignal(ActorTerminated.class, (state, signal) -> {
                        state.subscribers().remove(signal.ref());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    /** Sends 1, 2, 3, ... to its subscriber, the next one only after an {@link Ack}. */
    static class CounterActor implements SpringActor<CounterActor.Command> {
        interface Command {}

        record Start(SpringActorRef<Integer> subscriber) implements Command {}

        record Ack() implements Command {}

        /** How many numbers the actor has sent. */
        static final class Sent extends AskCommand<Integer> implements Command {}

        private static final class State {
            SpringActorRef<Integer> subscriber;
            int sent;

            void sendNext() {
                subscriber.tell(++sent);
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext ctx) {
            return SpringActorBehavior.builder(Command.class, ctx)
                    .withState(c -> new State())
                    .onMessage(Start.class, (state, msg) -> {
                        state.subscriber = msg.subscriber();
                        state.sendNext();
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Ack.class, (state, msg) -> {
                        state.sendNext();
                        return SpringActorBehavior.same();
                    })
                    .onMessage(Sent.class, (state, msg) -> {
                        msg.reply(state.sent);
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
}
