package io.github.seonwkim.actor.streams.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

/**
 * Bridges a {@link Publisher}, such as an {@code ActorStreams} {@code Flux}, to a gRPC response
 * stream. Use it in a server-streaming or unary method of a grpc-java or Spring gRPC service:
 *
 * <pre>{@code
 * @Override
 * public void watchPrices(WatchRequest request, StreamObserver<Price> responses) {
 *     ActorGrpc.respond(
 *             streams.messages(Price.class).flux(ref -> ticker.tell(new Subscribe(ref))),
 *             responses);
 * }
 * }</pre>
 *
 * <p>Elements are requested only while the call is ready for more, so a slow client slows the
 * publisher. When the client cancels or disconnects, the subscription is cancelled. A failed
 * publisher ends the call with the status of the error ({@code UNKNOWN} unless it carries one).
 */
public final class ActorGrpc {

    private ActorGrpc() {}

    /** Sends every element of {@code elements} to {@code responses}, then completes the call. */
    public static <T> void respond(Publisher<T> elements, StreamObserver<T> responses) {
        Flux.from(elements).subscribe(new ResponseSubscriber<>((ServerCallStreamObserver<T>) responses));
    }

    private static final class ResponseSubscriber<T> extends BaseSubscriber<T> {
        private final ServerCallStreamObserver<T> responses;
        private final AtomicBoolean waitingForReady = new AtomicBoolean();

        ResponseSubscriber(ServerCallStreamObserver<T> responses) {
            this.responses = responses;
            responses.setOnCancelHandler(this::cancel);
            responses.setOnReadyHandler(() -> {
                if (waitingForReady.compareAndSet(true, false)) {
                    request(1);
                }
            });
        }

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
            requestWhenReady();
        }

        @Override
        protected void hookOnNext(T value) {
            if (responses.isCancelled()) {
                return;
            }
            responses.onNext(value);
            requestWhenReady();
        }

        @Override
        protected void hookOnComplete() {
            responses.onCompleted();
        }

        @Override
        protected void hookOnError(Throwable error) {
            responses.onError(Status.fromThrowable(error).asRuntimeException());
        }

        /** Requests one element now if the call is ready, else when its ready handler runs. */
        private void requestWhenReady() {
            waitingForReady.set(true);
            if (responses.isReady() && waitingForReady.compareAndSet(true, false)) {
                request(1);
            }
        }
    }
}
