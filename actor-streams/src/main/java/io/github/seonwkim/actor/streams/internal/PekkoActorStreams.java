package io.github.seonwkim.actor.streams.internal;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.streams.ActorStreams;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.CompletionStrategy;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.AsPublisher;
import org.apache.pekko.stream.javadsl.JavaFlowSupport;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.typed.javadsl.ActorSource;
import org.reactivestreams.Publisher;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link ActorStreams} on the actor system's Pekko Streams materializer. */
public final class PekkoActorStreams implements ActorStreams {

    private final Materializer materializer;

    public PekkoActorStreams(SpringActorSystem system) {
        this.materializer = Materializer.matFromSystem(system.unwrap(ActorSystem.class));
    }

    @Override
    public <RES> Mono<RES> ask(SpringActorHandle.AskBuilder<RES> ask) {
        return Mono.fromCompletionStage(ask::execute);
    }

    @Override
    public <IN, RES> Flux<RES> askEach(
            Publisher<IN> elements, int parallelism, Function<? super IN, SpringActorHandle.AskBuilder<RES>> ask) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be at least 1, was " + parallelism);
        }
        // flatMapSequential requests `parallelism` elements upstream and one more per reply.
        return Flux.from(elements).flatMapSequential(element -> this.ask(ask.apply(element)), parallelism, 1);
    }

    @Override
    public <T> Messages<T> messages(Class<T> type) {
        return new PekkoMessages<>();
    }

    private final class PekkoMessages<T> implements Messages<T> {
        private int bufferSize = 256;
        private Overflow overflow = Overflow.FAIL;

        @Nullable private ActorRef<Object> ackTo;

        @Nullable private Object ack;

        private Predicate<? super T> isLast = message -> false;
        private Consumer<SpringActorRef<T>> onClose = ref -> {};

        @Override
        public Messages<T> bufferSize(int bufferSize) {
            if (bufferSize < 1) {
                throw new IllegalArgumentException("bufferSize must be at least 1, was " + bufferSize);
            }
            this.bufferSize = bufferSize;
            return this;
        }

        @Override
        public Messages<T> overflow(Overflow overflow) {
            this.overflow = overflow;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A> Messages<T> ackTo(SpringActorRef<A> sender, A ack) {
            this.ackTo = sender.unwrap(ActorRef.class);
            this.ack = ack;
            return this;
        }

        @Override
        public Messages<T> completeWhen(Predicate<? super T> isLast) {
            this.isLast = isLast;
            return this;
        }

        @Override
        public Messages<T> onClose(Consumer<SpringActorRef<T>> onClose) {
            this.onClose = onClose;
            return this;
        }

        @Override
        public Flux<T> flux(Consumer<SpringActorRef<T>> onSubscribe) {
            Source<T, ActorRef<T>> source = source();
            Consumer<SpringActorRef<T>> close = onClose;
            // Materialized per subscriber: each gets its own reference, stopped when it cancels.
            return Flux.defer(() -> {
                Pair<ActorRef<T>, Flow.Publisher<T>> running = source.toMat(
                                JavaFlowSupport.Sink.<T>asPublisher(AsPublisher.WITHOUT_FANOUT), Keep.both())
                        .run(materializer);
                SpringActorRef<T> ref = new PekkoActorRef<>(running.first());
                return JdkFlowAdapter.flowPublisherToFlux(running.second())
                        .doOnSubscribe(subscription -> onSubscribe.accept(ref))
                        .doFinally(signal -> close.accept(ref));
            });
        }

        private Source<T, ActorRef<T>> source() {
            Predicate<? super T> last = isLast;
            if (ackTo != null && ack != null) {
                return ActorSource.actorRefWithBackpressure(
                        ackTo,
                        ack,
                        message -> last.test(message) ? Optional.of(CompletionStrategy.draining()) : Optional.empty(),
                        message -> Optional.empty());
            }
            return ActorSource.actorRef(last::test, message -> Optional.empty(), bufferSize, overflowStrategy());
        }

        private OverflowStrategy overflowStrategy() {
            return switch (overflow) {
                case FAIL -> OverflowStrategy.fail();
                case DROP_OLDEST -> OverflowStrategy.dropHead();
                case DROP_NEWEST -> OverflowStrategy.dropNew();
            };
        }
    }
}
