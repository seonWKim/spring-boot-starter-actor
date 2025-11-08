package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

/**
 * Actor that processes data items by transforming and validating them.
 *
 * <p>This actor is used in various stream examples to demonstrate actor-stream integration.
 * It processes incoming data items and returns transformed results.
 */
@Component
public class DataProcessorActor implements SpringActor<DataProcessorActor.Command> {

    /** Base interface for all commands. */
    public interface Command {}

    /**
     * Command to process a data item.
     */
    public static class ProcessData extends AskCommand<ProcessedResult> implements Command {
        private final String data;

        public ProcessData(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }

    /**
     * Result of processing a data item.
     */
    public static class ProcessedResult {
        private final String original;
        private final String processed;
        private final long timestamp;

        public ProcessedResult(String original, String processed, long timestamp) {
            this.original = original;
            this.processed = processed;
            this.timestamp = timestamp;
        }

        public String getOriginal() {
            return original;
        }

        public String getProcessed() {
            return processed;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "ProcessedResult{original='" + original + "', processed='" + processed + "', timestamp="
                    + timestamp + '}';
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new DataProcessorBehavior(ctx, actorContext))
                .onMessage(ProcessData.class, DataProcessorBehavior::onProcessData)
                .build();
    }

    private static class DataProcessorBehavior {
        private final org.apache.pekko.actor.typed.javadsl.ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        DataProcessorBehavior(
                org.apache.pekko.actor.typed.javadsl.ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
        }

        private Behavior<Command> onProcessData(ProcessData msg) {
            // Simulate some processing
            String processed = msg.getData().toUpperCase() + "_PROCESSED";
            long timestamp = System.currentTimeMillis();

            ctx.getLog()
                    .debug(
                            "Processing data in actor {}: {} -> {}",
                            actorContext.actorId(),
                            msg.getData(),
                            processed);

            // Reply with the processed result
            msg.reply(new ProcessedResult(msg.getData(), processed, timestamp));

            return Behaviors.same();
        }
    }
}
