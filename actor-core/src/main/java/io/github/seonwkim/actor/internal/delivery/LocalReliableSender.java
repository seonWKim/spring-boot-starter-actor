package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.delivery.ReliableSender;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.delivery.ConsumerController;
import org.apache.pekko.actor.typed.delivery.ProducerController;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;

/**
 * {@link ReliableSender} to one local actor: a system actor that owns a Pekko {@link ProducerController}.
 *
 * <p>It asks the consumer for its controller ({@link ReliableConsumerModule.Connect}) and watches it.
 * When the controller stops, because a delivery failed or the consumer restarted, it asks again and
 * registers the new controller, and the producer controller resends every unconfirmed message.
 */
public final class LocalReliableSender<T> implements ReliableSender<T> {

    /** How often to ask a consumer that hasn't answered for its controller. */
    static final Duration CONNECT_RETRY = Duration.ofSeconds(1);

    /** Unanswered connects before a warning that the consumer may not be reliable. */
    private static final int CONNECT_TRIES_BEFORE_WARNING = 3;

    private final ActorRef<Command> actor;

    private LocalReliableSender(ActorRef<Command> actor) {
        this.actor = actor;
    }

    public static <T> LocalReliableSender<T> start(ActorSystem<?> system, ActorRef<T> consumer, String producerId) {
        @SuppressWarnings("unchecked")
        ActorRef<Object> target = (ActorRef<Object>) (ActorRef<?>) consumer;
        ActorRef<Command> actor = system.systemActorOf(
                Behaviors.setup(
                        ctx -> Behaviors.withTimers(timers -> new State(ctx, timers, target, producerId).behavior())),
                SenderNames.of("reliable-sender", producerId),
                Props.empty());
        return new LocalReliableSender<>(actor);
    }

    @Override
    public CompletionStage<Void> send(T message) {
        CompletableFuture<Void> confirmed = new CompletableFuture<>();
        actor.tell(new Send(message, confirmed));
        return confirmed;
    }

    @Override
    public void close() {
        actor.tell(Close.INSTANCE);
    }

    interface Command {}

    private record Send(Object message, CompletableFuture<Void> confirmed) implements Command {}

    private record Next(ProducerController.RequestNext<Object> requestNext) implements Command {}

    private record Confirmed(long seqNr) implements Command {}

    private record ControllerFound(ActorRef<ConsumerController.Command<Object>> controller) implements Command {}

    private record ControllerStopped(ActorRef<?> controller) implements Command {}

    private enum ConsumerStopped implements Command {
        INSTANCE
    }

    private enum RetryConnect implements Command {
        INSTANCE
    }

    private enum Close implements Command {
        INSTANCE
    }

    /** The sender actor's state; only the actor touches it. */
    private static final class State {
        private final ActorContext<Command> ctx;
        private final TimerScheduler<Command> timers;
        private final ActorRef<Object> consumer;
        private final String producerId;
        private final ActorRef<ProducerController.Command<Object>> producer;
        /** Typed {@code Object} because Scala's {@code ActorRef[Long]} erases to it; replies are {@link Long}s. */
        private final ActorRef<Object> confirmations;

        private final ActorRef<ReliableConsumerModule.Connected> connected;
        private final PendingConfirmations<Object> pending = new PendingConfirmations<>();
        private final Map<Long, CompletableFuture<Void>> bySeqNr = new HashMap<>();

        @Nullable private ProducerController.RequestNext<Object> demand;

        @Nullable private ActorRef<ConsumerController.Command<Object>> controller;

        private int unansweredConnects;

        @SuppressWarnings("unchecked")
        State(ActorContext<Command> ctx, TimerScheduler<Command> timers, ActorRef<Object> consumer, String producerId) {
            this.ctx = ctx;
            this.timers = timers;
            this.consumer = consumer;
            this.producerId = producerId;
            this.producer =
                    ctx.spawn(ProducerController.create(Object.class, producerId, Optional.empty()), "producer");
            this.confirmations = (ActorRef<Object>) (ActorRef<?>) ctx.messageAdapter(Long.class, Confirmed::new);
            this.connected = ctx.messageAdapter(
                    ReliableConsumerModule.Connected.class, c -> new ControllerFound(c.controller()));
            ActorRef<ProducerController.RequestNext<Object>> requestNext =
                    ctx.messageAdapter(ProducerController.requestNextClass(), Next::new);
            producer.tell(new ProducerController.Start<>(requestNext));
            ctx.watchWith(consumer, ConsumerStopped.INSTANCE);
            connect();
        }

        Behavior<Command> behavior() {
            return Behaviors.receive(Command.class)
                    .onMessage(Send.class, this::onSend)
                    .onMessage(Next.class, next -> {
                        demand = next.requestNext();
                        return sendNext();
                    })
                    .onMessage(Confirmed.class, confirmed -> {
                        CompletableFuture<Void> stage = bySeqNr.remove(confirmed.seqNr());
                        if (stage != null) {
                            stage.complete(null);
                        }
                        return Behaviors.same();
                    })
                    .onMessage(ControllerFound.class, this::onControllerFound)
                    .onMessage(ControllerStopped.class, stopped -> {
                        if (stopped.controller().equals(controller)) {
                            controller = null;
                            connect();
                        }
                        return Behaviors.same();
                    })
                    .onMessage(RetryConnect.class, retry -> {
                        if (++unansweredConnects == CONNECT_TRIES_BEFORE_WARNING) {
                            ctx.getLog()
                                    .warn(
                                            "Reliable sender {} gets no answer from {}: is its behavior wrapped"
                                                    + " with ReliableDelivery.consumer(...)?",
                                            producerId,
                                            consumer.path());
                        }
                        consumer.tell(new ReliableConsumerModule.Connect(producerId, connected));
                        return Behaviors.same();
                    })
                    .onMessage(ConsumerStopped.class, stopped -> {
                        pending.failAll("The consumer " + consumer.path() + " stopped");
                        return Behaviors.stopped();
                    })
                    .onMessage(Close.class, close -> Behaviors.stopped())
                    .onSignal(PostStop.class, stop -> {
                        pending.failAll("The reliable sender " + producerId + " was closed");
                        return Behaviors.same();
                    })
                    .build();
        }

        private Behavior<Command> onSend(Send send) {
            pending.add(send.message(), send.confirmed());
            return sendNext();
        }

        private Behavior<Command> sendNext() {
            if (demand == null) {
                return Behaviors.same();
            }
            PendingConfirmations.Waiting<Object> next = pending.next();
            if (next != null) {
                bySeqNr.put(demand.currentSeqNr(), next.confirmed());
                demand.askNextTo()
                        .tell(new ProducerController.MessageWithConfirmation<>(next.message(), confirmations));
                demand = null;
            }
            return Behaviors.same();
        }

        private Behavior<Command> onControllerFound(ControllerFound found) {
            if (!found.controller().equals(controller)) {
                controller = found.controller();
                unansweredConnects = 0;
                timers.cancel(RetryConnect.INSTANCE);
                ctx.watchWith(controller, new ControllerStopped(controller));
                producer.tell(new ProducerController.RegisterConsumer<>(controller));
            }
            return Behaviors.same();
        }

        private void connect() {
            consumer.tell(new ReliableConsumerModule.Connect(producerId, connected));
            timers.startTimerWithFixedDelay(RetryConnect.INSTANCE, CONNECT_RETRY);
        }
    }
}
