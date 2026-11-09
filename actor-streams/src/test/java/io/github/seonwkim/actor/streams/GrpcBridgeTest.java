package io.github.seonwkim.actor.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.streams.TestActors.CounterActor;
import io.github.seonwkim.actor.streams.TestActors.TickerActor;
import io.github.seonwkim.actor.streams.grpc.ActorGrpc;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/** A server-streaming gRPC method on {@link ActorGrpc}, over the in-process transport. */
class GrpcBridgeTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @EnableActorSupport
    static class App {}

    /** Plain UTF-8 strings, so the test needs no generated protobuf classes. */
    private static final MethodDescriptor.Marshaller<String> UTF8 = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private static final MethodDescriptor<String, String> WATCH = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName("test.Ticker/Watch")
            .setRequestMarshaller(UTF8)
            .setResponseMarshaller(UTF8)
            .build();

    private static final MethodDescriptor<String, String> GET = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.Ticker/Get")
            .setRequestMarshaller(UTF8)
            .setResponseMarshaller(UTF8)
            .build();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(App.class)
            .withBean(TickerActor.class)
            .withBean(CounterActor.class);

    private static <C> SpringActorHandle<C> spawn(ApplicationContext context, Class<?> type) throws Exception {
        @SuppressWarnings({"unchecked", "rawtypes"})
        SpringActorHandle<C> handle = (SpringActorHandle<C>) context.getBean(SpringActorSystem.class)
                .actor((Class) type)
                .withId(type.getSimpleName())
                .spawn()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        return handle;
    }

    /** Serves WATCH with {@code respond}, runs {@code test} with a channel to it, then shuts both down. */
    private static void serve(ServerCalls.ServerStreamingMethod<String, String> respond, ChannelTest test)
            throws Exception {
        String name = UUID.randomUUID().toString();
        Server server = InProcessServerBuilder.forName(name)
                .addService(ServerServiceDefinition.builder("test.Ticker")
                        .addMethod(WATCH, ServerCalls.asyncServerStreamingCall(respond))
                        .addMethod(GET, ServerCalls.asyncUnaryCall(respond::invoke))
                        .build())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name).build();
        try {
            test.run(channel);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    interface ChannelTest {
        void run(ManagedChannel channel) throws Exception;
    }

    /** Collects responses; requests {@code initial} of them, then only on {@link #request}. */
    private static final class Client implements ClientResponseObserver<String, String> {
        final List<String> received = new CopyOnWriteArrayList<>();
        final AtomicReference<ClientCallStreamObserver<String>> call = new AtomicReference<>();
        private final int initial;

        Client(int initial) {
            this.initial = initial;
        }

        @Override
        public void beforeStart(ClientCallStreamObserver<String> requestStream) {
            requestStream.disableAutoRequestWithInitial(initial);
            call.set(requestStream);
        }

        @Override
        public void onNext(String value) {
            received.add(value);
        }

        @Override
        public void onError(Throwable t) {}

        @Override
        public void onCompleted() {}
    }

    @Test
    void aClientCancelStopsTheActorSideSubscription() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<TickerActor.Command> ticker = spawn(context, TickerActor.class);

            serve(
                    (request, responses) -> ActorGrpc.respond(
                            streams.messages(String.class).flux(ref -> ticker.tell(new TickerActor.Subscribe(ref))),
                            responses),
                    channel -> {
                        Client client = new Client(Integer.MAX_VALUE);
                        ClientCalls.asyncServerStreamingCall(
                                channel.newCall(WATCH, CallOptions.DEFAULT), "watch", client);
                        await().atMost(TIMEOUT).until(() -> TestActors.subscribers(ticker) == 1);
                        ticker.tell(new TickerActor.Publish("a"));
                        await().atMost(TIMEOUT).until(() -> client.received.equals(List.of("a")));

                        client.call.get().cancel("client leaves", null);
                        await().atMost(TIMEOUT).until(() -> TestActors.subscribers(ticker) == 0);
                    });
        });
    }

    @Test
    void aUnaryMethodAnswersWithTheMonoOfAnAsk() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<TickerActor.Command> ticker = spawn(context, TickerActor.class);

            serve(
                    (request, responses) -> ActorGrpc.respond(
                            streams.ask(ticker.ask(new TickerActor.Subscribers())
                                            .withTimeout(TIMEOUT))
                                    .map(count -> request + "=" + count),
                            responses),
                    channel -> assertThat(ClientCalls.blockingUnaryCall(
                                    channel,
                                    GET,
                                    CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS),
                                    "subscribers"))
                            .isEqualTo("subscribers=0"));
        });
    }

    @Test
    void aSlowClientSlowsTheActor() {
        runner.run(context -> {
            ActorStreams streams = context.getBean(ActorStreams.class);
            SpringActorHandle<CounterActor.Command> counter = spawn(context, CounterActor.class);

            serve(
                    (request, responses) -> ActorGrpc.respond(
                            streams.messages(Integer.class)
                                    .ackTo(counter, new CounterActor.Ack())
                                    .flux(ref -> counter.tell(new CounterActor.Start(ref)))
                                    .map(String::valueOf),
                            responses),
                    channel -> {
                        Client client = new Client(2);
                        ClientCalls.asyncServerStreamingCall(
                                channel.newCall(WATCH, CallOptions.DEFAULT), "count", client);
                        await().atMost(TIMEOUT).until(() -> client.received.equals(List.of("1", "2")));
                        await().during(Duration.ofMillis(300)).atMost(TIMEOUT).until(() -> sent(counter) < 20);

                        client.call.get().request(3);
                        await().atMost(TIMEOUT).until(() -> client.received.size() == 5);
                        assertThat(client.received).containsExactly("1", "2", "3", "4", "5");
                    });
        });
    }

    private static int sent(SpringActorHandle<CounterActor.Command> counter) throws Exception {
        return counter.ask(new CounterActor.Sent())
                .withTimeout(TIMEOUT)
                .execute()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }
}
