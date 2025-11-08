package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating how to use actors as stream sources.
 *
 * <p>This example shows different patterns for creating streams from actors:
 * <ul>
 *   <li>Actor that produces messages on demand</li>
 *   <li>Actor that pushes messages to a queue</li>
 *   <li>Polling an actor for data</li>
 * </ul>
 */
@Service
public class ActorSourceExample {

    private final SpringActorSystem actorSystem;

    public ActorSourceExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Creates a stream from an actor using polling pattern.
     *
     * <p>This pattern polls the actor at regular intervals to get data.
     * Useful when the actor produces data on demand.
     *
     * @return CompletionStage with collected results
     */
    public CompletionStage<List<String>> streamFromActorPolling() {
        return actorSystem
                .getOrSpawn(MessageProducerActor.class, "producer")
                .thenCompose(producer ->
                        // Create a stream that polls the actor every 100ms
                        Source.tick(Duration.ZERO, Duration.ofMillis(100), "tick")
                                .take(20) // Take 20 items
                                .mapAsync(
                                        5,
                                        tick -> producer
                                                .ask(new MessageProducerActor.GetMessage())
                                                .withTimeout(Duration.ofSeconds(1))
                                                .execute())
                                .runWith(Sink.seq(), actorSystem.getRaw()));
    }

    /**
     * Creates a stream using Source.queue pattern.
     *
     * <p>This pattern creates a queue that can be fed from actors.
     * The actor can push messages to the queue, and the stream will process them.
     *
     * @return A SourceQueueWithComplete that can be used to push messages
     */
    public SourceQueueWithComplete<String> createQueueSource() {
        // Create a source with a queue
        Source<String, SourceQueueWithComplete<String>> queueSource =
                Source.queue(100, OverflowStrategy.backpressure());

        // Materialize the source to get the queue
        SourceQueueWithComplete<String> queue = queueSource
                .map(msg -> {
                    System.out.println("Processing from queue: " + msg);
                    return msg.toUpperCase();
                })
                .to(Sink.foreach(result -> System.out.println("Result: " + result)))
                .run(actorSystem.getRaw());

        return queue;
    }

    /**
     * Demonstrates actor pushing messages to a queue-backed stream.
     *
     * @return CompletionStage that completes when stream finishes
     */
    public CompletionStage<List<String>> actorPushingToStream() {
        // Create a queue source
        Source<String, SourceQueueWithComplete<String>> queueSource =
                Source.queue(100, OverflowStrategy.backpressure());

        // Start the stream and get the queue
        SourceQueueWithComplete<String> queue = queueSource
                .toMat(Sink.seq(), (q, future) -> {
                    // Return the queue for pushing messages
                    return q;
                })
                .run(actorSystem.getRaw());

        // Create an actor that will push messages to the queue
        return actorSystem
                .getOrSpawn(QueuePusherActor.class, "pusher")
                .thenCompose(pusher -> {
                    // Tell the actor to start pushing messages
                    pusher.tell(new QueuePusherActor.StartPushing(queue, 10));

                    // The actor will push messages, and we wait for completion
                    return queue.watchCompletion().thenApply(done -> List.of("Completed"));
                });
    }

    /**
     * Creates a bounded source from a list of messages.
     *
     * <p>This is useful when an actor has accumulated messages that need to be processed as a stream.
     *
     * @return CompletionStage with processed results
     */
    public CompletionStage<List<String>> boundedSourceFromActor() {
        return actorSystem
                .getOrSpawn(MessageProducerActor.class, "bounded-producer")
                .thenCompose(producer ->
                        // Get all messages from actor and create a stream
                        producer.ask(new MessageProducerActor.GetAllMessages())
                                .withTimeout(Duration.ofSeconds(5))
                                .execute()
                                .thenCompose(messages -> Source.from(messages)
                                        .map(String::toUpperCase)
                                        .runWith(Sink.seq(), actorSystem.getRaw())));
    }

    /**
     * Actor that produces messages on demand.
     */
    @Component
    public static class MessageProducerActor implements SpringActor<MessageProducerActor.Command> {

        public interface Command {}

        public static class GetMessage extends AskCommand<String> implements Command {}

        public static class GetAllMessages extends AskCommand<List<String>> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new ProducerBehavior(ctx))
                    .onMessage(GetMessage.class, ProducerBehavior::onGetMessage)
                    .onMessage(GetAllMessages.class, ProducerBehavior::onGetAllMessages)
                    .build();
        }

        private static class ProducerBehavior {
            private final ActorContext<Command> ctx;
            private int counter = 0;

            ProducerBehavior(ActorContext<Command> ctx) {
                this.ctx = ctx;
            }

            private Behavior<Command> onGetMessage(GetMessage msg) {
                String message = "Message-" + (++counter);
                ctx.getLog().debug("Producing message: {}", message);
                msg.reply(message);
                return Behaviors.same();
            }

            private Behavior<Command> onGetAllMessages(GetAllMessages msg) {
                List<String> messages =
                        List.of("Message-A", "Message-B", "Message-C", "Message-D", "Message-E");
                ctx.getLog().debug("Producing all messages: {}", messages.size());
                msg.reply(messages);
                return Behaviors.same();
            }
        }
    }

    /**
     * Actor that pushes messages to a queue.
     */
    @Component
    public static class QueuePusherActor implements SpringActor<QueuePusherActor.Command> {

        public interface Command {}

        public static class StartPushing implements Command {
            private final SourceQueueWithComplete<String> queue;
            private final int count;

            public StartPushing(SourceQueueWithComplete<String> queue, int count) {
                this.queue = queue;
                this.count = count;
            }

            public SourceQueueWithComplete<String> getQueue() {
                return queue;
            }

            public int getCount() {
                return count;
            }
        }

        private static class PushNext implements Command {
            private final SourceQueueWithComplete<String> queue;
            private final int remaining;

            public PushNext(SourceQueueWithComplete<String> queue, int remaining) {
                this.queue = queue;
                this.remaining = remaining;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new PusherBehavior(ctx))
                    .onMessage(StartPushing.class, PusherBehavior::onStartPushing)
                    .onMessage(PushNext.class, PusherBehavior::onPushNext)
                    .build();
        }

        private static class PusherBehavior {
            private final ActorContext<Command> ctx;
            private int counter = 0;

            PusherBehavior(ActorContext<Command> ctx) {
                this.ctx = ctx;
            }

            private Behavior<Command> onStartPushing(StartPushing msg) {
                ctx.getLog().info("Starting to push {} messages", msg.getCount());
                ctx.getSelf().tell(new PushNext(msg.getQueue(), msg.getCount()));
                return Behaviors.same();
            }

            private Behavior<Command> onPushNext(PushNext msg) {
                if (msg.remaining <= 0) {
                    ctx.getLog().info("Finished pushing messages, completing queue");
                    msg.queue.complete();
                    return Behaviors.same();
                }

                String message = "QueueMessage-" + (++counter);
                ctx.getLog().debug("Pushing message: {}", message);

                // Offer the message to the queue
                msg.queue.offer(message);

                // Schedule next push
                ctx.scheduleOnce(Duration.ofMillis(50), ctx.getSelf(), new PushNext(msg.queue, msg.remaining - 1));

                return Behaviors.same();
            }
        }
    }
}
