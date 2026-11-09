package io.github.seonwkim.actor.streams;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactor bridges to actors: a {@link Mono} of an ask, a {@link Flux} that feeds an actor with
 * backpressure, and a {@link Flux} of the messages an actor sends. The bean exists when
 * {@code actor-streams} is on the classpath of an application with {@code @EnableActorSupport}.
 *
 * <pre>{@code
 * Mono<Order> order = streams.ask(orders.ask(new GetOrder(id)));
 *
 * Flux<Receipt> receipts = streams.askEach(payments, 8, p -> ledger.ask(new Charge(p)));
 *
 * Flux<Price> prices = streams.messages(Price.class)
 *     .bufferSize(100)
 *     .onClose(ref -> ticker.tell(new Unsubscribe(ref)))
 *     .flux(ref -> ticker.tell(new Subscribe(ref)));
 * }</pre>
 */
public interface ActorStreams {

    /**
     * A {@link Mono} of the reply. The command is sent when the {@code Mono} is subscribed, and again
     * on each new subscription; the builder's timeout and fallback apply.
     */
    <RES> Mono<RES> ask(SpringActorHandle.AskBuilder<RES> ask);

    /**
     * Asks an actor about each element of {@code elements} and emits the replies in the order of the
     * elements. At most {@code parallelism} asks are in flight: a slow actor slows the demand on
     * {@code elements} instead of filling its mailbox. A failed ask (for example a timeout) fails the
     * {@code Flux}; use the builder's {@code onTimeout} for a fallback reply instead.
     *
     * @param elements what to send
     * @param parallelism the most asks waiting for a reply at once, at least 1
     * @param ask builds the ask for one element, e.g. {@code n -> counter.ask(new Add(n))}
     */
    <IN, RES> Flux<RES> askEach(
            Publisher<IN> elements, int parallelism, Function<? super IN, SpringActorHandle.AskBuilder<RES>> ask);

    /**
     * A {@link Flux} of the messages actors send to a reference that the {@code Flux} creates for
     * each subscriber. Configure it, then call {@link Messages#flux(Consumer)}.
     *
     * @param type the message type, for the compiler only
     */
    <T> Messages<T> messages(Class<T> type);

    /**
     * Settings of a {@link #messages(Class)} {@code Flux}.
     *
     * <p>By default the reference buffers up to 256 messages that the subscriber has not requested
     * yet, and the {@code Flux} fails when the buffer is full ({@link Overflow#FAIL}). With
     * {@link #ackTo} the sender waits for an acknowledgement after each message instead, so nothing
     * is buffered or dropped.
     *
     * <p>When the subscriber cancels (for example a client disconnects), the reference stops: an actor
     * that {@code watch}es it receives {@code ActorTerminated}, and messages sent to it afterwards go to
     * dead letters.
     *
     * @param <T> the message type
     */
    interface Messages<T> {

        /** The most messages kept for a subscriber that has not requested them yet. Default 256. */
        Messages<T> bufferSize(int bufferSize);

        /** What happens when the buffer is full. Default {@link Overflow#FAIL}. */
        Messages<T> overflow(Overflow overflow);

        /**
         * The sender waits for {@code ack} after each message it sends: the reference sends
         * {@code ack} to {@code sender} once the message is passed on to the subscriber. Replaces the
         * buffer, so {@link #bufferSize} and {@link #overflow} do not apply.
         */
        <A> Messages<T> ackTo(SpringActorRef<A> sender, A ack);

        /**
         * The message that ends the {@code Flux}: it completes after the messages before it, and this
         * message is not emitted.
         */
        Messages<T> completeWhen(Predicate<? super T> isLast);

        /**
         * Runs when the {@code Flux} ends for this subscriber (completed, failed or cancelled), with the
         * reference given to {@link #flux}, e.g. to tell an actor to forget it.
         */
        Messages<T> onClose(Consumer<SpringActorRef<T>> onClose);

        /**
         * The {@code Flux}. Each subscriber gets its own reference, passed to {@code onSubscribe}, e.g.
         * {@code ref -> ticker.tell(new Subscribe(ref))}.
         */
        Flux<T> flux(Consumer<SpringActorRef<T>> onSubscribe);
    }

    /** What a {@link #messages(Class)} reference does with a message when its buffer is full. */
    enum Overflow {
        /** Fail the {@code Flux} with an error. */
        FAIL,
        /** Drop the oldest buffered message to make room. */
        DROP_OLDEST,
        /** Drop the new message. */
        DROP_NEWEST
    }
}
