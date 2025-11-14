package io.github.seonwkim.example.receptionist;

import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import java.util.Random;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Worker actor that processes data processing tasks.
 *
 * <p>This actor demonstrates:
 * <ul>
 *   <li>Processing tasks with simulated work duration
 *   <li>Returning results via ask pattern
 *   <li>Logging for visibility
 * </ul>
 */
@Component
public class DataProcessorWorker implements SpringActor<DataProcessorWorker.Command> {

    private static final Logger logger = LoggerFactory.getLogger(DataProcessorWorker.class);
    private final Random random = new Random();

    public interface Command {}

    /**
     * Command to process a data batch.
     */
    public static class ProcessBatch extends AskCommand<ProcessingResult> implements Command {
        public final String batchId;
        public final int recordCount;

        public ProcessBatch(String batchId, int recordCount) {
            this.batchId = batchId;
            this.recordCount = recordCount;
        }
    }

    /**
     * Result of processing a batch.
     */
    public static class ProcessingResult {
        public final String batchId;
        public final String workerId;
        public final int recordsProcessed;
        public final long processingTimeMs;
        public final boolean success;

        public ProcessingResult(
                String batchId, String workerId, int recordsProcessed, long processingTimeMs, boolean success) {
            this.batchId = batchId;
            this.workerId = workerId;
            this.recordsProcessed = recordsProcessed;
            this.processingTimeMs = processingTimeMs;
            this.success = success;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new WorkerBehavior(ctx, actorContext))
                .onMessage(ProcessBatch.class, WorkerBehavior::onProcessBatch)
                .build();
    }

    private class WorkerBehavior {
        private final org.apache.pekko.actor.typed.javadsl.ActorContext<Command> ctx;
        private final SpringActorContext actorContext;

        WorkerBehavior(
                org.apache.pekko.actor.typed.javadsl.ActorContext<Command> ctx, SpringActorContext actorContext) {
            this.ctx = ctx;
            this.actorContext = actorContext;
            ctx.getLog().info("Worker {} started and ready to process batches", actorContext.actorId());
        }

        org.apache.pekko.actor.typed.Behavior<Command> onProcessBatch(ProcessBatch msg) {
            ctx.getLog()
                    .info(
                            "Worker {} processing batch {} with {} records",
                            actorContext.actorId(),
                            msg.batchId,
                            msg.recordCount);

            long startTime = System.currentTimeMillis();

            try {
                // Simulate processing work (50-200ms per record)
                int processingTimePerRecord = 50 + random.nextInt(150);
                Thread.sleep((long) msg.recordCount * processingTimePerRecord / 10);

                long processingTime = System.currentTimeMillis() - startTime;

                ProcessingResult result = new ProcessingResult(
                        msg.batchId, actorContext.actorId(), msg.recordCount, processingTime, true);

                ctx.getLog()
                        .info(
                                "Worker {} completed batch {} in {}ms",
                                actorContext.actorId(),
                                msg.batchId,
                                processingTime);

                msg.reply(result);

            } catch (InterruptedException e) {
                ctx.getLog()
                        .error("Worker {} interrupted while processing batch {}", actorContext.actorId(), msg.batchId);
                Thread.currentThread().interrupt();

                long processingTime = System.currentTimeMillis() - startTime;
                ProcessingResult result =
                        new ProcessingResult(msg.batchId, actorContext.actorId(), 0, processingTime, false);
                msg.reply(result);
            }

            return Behaviors.same();
        }
    }
}
