package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating how to use actors as stream sinks.
 *
 * <p>This example shows different patterns for consuming stream data with actors:
 * <ul>
 *   <li>Actor as a sink that processes each element</li>
 *   <li>Actor that accumulates results</li>
 *   <li>Actor that batches and processes data</li>
 *   <li>Using ask pattern to send to actors</li>
 * </ul>
 */
@Service
public class ActorSinkExample {

    private final SpringActorSystem actorSystem;

    public ActorSinkExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Sends stream elements to an actor using tell (fire-and-forget).
     *
     * <p>This is the simplest pattern - just tell the actor about each element.
     * No backpressure from the actor, stream controls the rate.
     *
     * @param data Input data
     * @return CompletionStage that completes when all messages are sent
     */
    public CompletionStage<Done> streamToActorWithTell(List<String> data) {
        return actorSystem
                .getOrSpawn(MessageConsumerActor.class, "consumer")
                .thenCompose(consumer -> Source.from(data)
                        .map(item -> {
                            consumer.tell(new MessageConsumerActor.ProcessMessage(item));
                            return item;
                        })
                        .runWith(Sink.ignore(), actorSystem.getRaw()));
    }

    /**
     * Sends stream elements to an actor using ask pattern with backpressure.
     *
     * <p>This pattern provides backpressure - the stream will slow down
     * if the actor cannot keep up with the message rate.
     *
     * @param data Input data
     * @return CompletionStage that completes when all items are processed
     */
    public CompletionStage<Done> streamToActorWithAsk(List<String> data) {
        return actorSystem
                .getOrSpawn(MessageConsumerActor.class, "consumer-ask")
                .thenCompose(consumer -> Source.from(data)
                        .mapAsync(
                                5, // parallelism
                                item -> consumer
                                        .ask(new MessageConsumerActor.ProcessAndAck(item))
                                        .withTimeout(Duration.ofSeconds(5))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw()));
    }

    /**
     * Accumulates all stream results in an actor.
     *
     * <p>This pattern shows how an actor can collect results from a stream.
     * Useful for aggregating stream data.
     *
     * @param data Input data
     * @return CompletionStage with accumulated results
     */
    public CompletionStage<List<String>> accumulateInActor(List<String> data) {
        return actorSystem
                .getOrSpawn(MessageConsumerActor.class, "accumulator")
                .thenCompose(consumer -> {
                    // Send all items to the actor
                    CompletionStage<Done> processing = Source.from(data)
                            .mapAsync(
                                    5,
                                    item -> consumer
                                            .ask(new MessageConsumerActor.ProcessAndAck(item))
                                            .withTimeout(Duration.ofSeconds(5))
                                            .execute())
                            .runWith(Sink.ignore(), actorSystem.getRaw());

                    // After processing, get accumulated results
                    return processing.thenCompose(done -> consumer
                            .ask(new MessageConsumerActor.GetAccumulated())
                            .withTimeout(Duration.ofSeconds(5))
                            .execute());
                });
    }

    /**
     * Batches stream data and sends batches to an actor.
     *
     * <p>This pattern is efficient when actor processing benefits from batching.
     *
     * @param data Input data
     * @param batchSize Size of each batch
     * @return CompletionStage that completes when done
     */
    public CompletionStage<Done> streamBatchesToActor(List<String> data, int batchSize) {
        return actorSystem
                .getOrSpawn(MessageConsumerActor.class, "batch-consumer")
                .thenCompose(consumer -> Source.from(data)
                        .grouped(batchSize)
                        .mapAsync(
                                3,
                                batch -> consumer
                                        .ask(new MessageConsumerActor.ProcessBatch(batch))
                                        .withTimeout(Duration.ofSeconds(10))
                                        .execute())
                        .runWith(Sink.ignore(), actorSystem.getRaw()));
    }

    /**
     * Actor that consumes messages from streams.
     */
    @Component
    public static class MessageConsumerActor implements SpringActor<MessageConsumerActor.Command> {

        public interface Command {}

        /**
         * Fire-and-forget message processing.
         */
        public static class ProcessMessage implements Command {
            private final String message;

            public ProcessMessage(String message) {
                this.message = message;
            }

            public String getMessage() {
                return message;
            }
        }

        /**
         * Request-response message processing with acknowledgment.
         */
        public static class ProcessAndAck extends AskCommand<Done> implements Command {
            private final String message;

            public ProcessAndAck(String message) {
                this.message = message;
            }

            public String getMessage() {
                return message;
            }
        }

        /**
         * Process a batch of messages.
         */
        public static class ProcessBatch extends AskCommand<Done> implements Command {
            private final List<String> messages;

            public ProcessBatch(List<String> messages) {
                this.messages = messages;
            }

            public List<String> getMessages() {
                return messages;
            }
        }

        /**
         * Get all accumulated messages.
         */
        public static class GetAccumulated extends AskCommand<List<String>> implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new ConsumerBehavior(ctx, actorContext))
                    .onMessage(ProcessMessage.class, ConsumerBehavior::onProcessMessage)
                    .onMessage(ProcessAndAck.class, ConsumerBehavior::onProcessAndAck)
                    .onMessage(ProcessBatch.class, ConsumerBehavior::onProcessBatch)
                    .onMessage(GetAccumulated.class, ConsumerBehavior::onGetAccumulated)
                    .build();
        }

        private static class ConsumerBehavior {
            private final ActorContext<Command> ctx;
            private final SpringActorContext actorContext;
            private final List<String> accumulated = new ArrayList<>();
            private int processedCount = 0;

            ConsumerBehavior(ActorContext<Command> ctx, SpringActorContext actorContext) {
                this.ctx = ctx;
                this.actorContext = actorContext;
            }

            private Behavior<Command> onProcessMessage(ProcessMessage msg) {
                // Simple processing
                String processed = processItem(msg.getMessage());
                accumulated.add(processed);
                return Behaviors.same();
            }

            private Behavior<Command> onProcessAndAck(ProcessAndAck msg) {
                // Process and acknowledge
                String processed = processItem(msg.getMessage());
                accumulated.add(processed);
                msg.reply(Done.getInstance());
                return Behaviors.same();
            }

            private Behavior<Command> onProcessBatch(ProcessBatch msg) {
                ctx.getLog().info("Processing batch of {} items", msg.getMessages().size());

                // Process all items in the batch
                for (String item : msg.getMessages()) {
                    String processed = processItem(item);
                    accumulated.add(processed);
                }

                msg.reply(Done.getInstance());
                return Behaviors.same();
            }

            private Behavior<Command> onGetAccumulated(GetAccumulated msg) {
                ctx.getLog().info("Returning {} accumulated items", accumulated.size());
                msg.reply(new ArrayList<>(accumulated));
                return Behaviors.same();
            }

            private String processItem(String item) {
                processedCount++;
                String result = item.toUpperCase() + "_CONSUMED";
                ctx.getLog()
                        .debug(
                                "Actor {} consumed item #{}: {} -> {}",
                                actorContext.actorId(),
                                processedCount,
                                item,
                                result);
                return result;
            }
        }
    }
}
