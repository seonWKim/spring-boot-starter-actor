package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.SpringActorSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.stream.javadsl.FileIO;
import org.apache.pekko.stream.javadsl.Framing;
import org.apache.pekko.util.ByteString;
import org.springframework.stereotype.Service;

/**
 * Example demonstrating file processing using Pekko Streams with Spring actors.
 *
 * <p>This example shows how to:
 * <ul>
 *   <li>Read lines from a file using Pekko Streams</li>
 *   <li>Process each line through a Spring actor</li>
 *   <li>Handle backpressure automatically</li>
 *   <li>Write results to an output file</li>
 * </ul>
 *
 * <p>This pattern is useful for processing large files that don't fit in memory,
 * with automatic backpressure when actors can't keep up with the file read rate.
 */
@Service
public class FileProcessingExample {

    private final SpringActorSystem actorSystem;

    public FileProcessingExample(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Processes a file line by line using actors.
     *
     * <p>This method demonstrates:
     * <ul>
     *   <li>Reading from a file using FileIO source</li>
     *   <li>Framing lines using Pekko's built-in framing</li>
     *   <li>Processing each line through an actor using ask pattern</li>
     *   <li>Writing results to an output file</li>
     *   <li>Automatic backpressure handling</li>
     * </ul>
     *
     * @param inputPath Path to the input file
     * @param outputPath Path to the output file
     * @return A CompletionStage that completes when processing is done
     */
    public CompletionStage<Long> processFile(String inputPath, String outputPath) {
        // Get or spawn the actor
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "processor")
                .thenCompose(processor ->
                        // Create and run the stream pipeline
                        FileIO.fromPath(Paths.get(inputPath))
                                // Frame the byte stream into lines (handles backpressure)
                                .via(Framing.delimiter(ByteString.fromString("\n"), 1024))
                                // Convert bytes to strings
                                .map(ByteString::utf8String)
                                // Filter empty lines
                                .filter(line -> !line.trim().isEmpty())
                                // Process through actor with ask pattern (built-in timeout and backpressure)
                                .mapAsync(
                                        10, // parallelism: max 10 concurrent asks
                                        line -> processor
                                                .ask(new DataProcessorActor.ProcessData(line))
                                                .withTimeout(Duration.ofSeconds(5))
                                                .execute())
                                // Convert result to output format
                                .map(result -> result.getProcessed() + "\n")
                                // Convert to ByteString for file writing
                                .map(ByteString::fromString)
                                // Write to output file and return the count of bytes written
                                .runWith(FileIO.toPath(Paths.get(outputPath)), actorSystem.getRaw())
                                .thenApply(ioResult -> ioResult.count()));
    }

    /**
     * Processes a file with custom parallelism and timeout.
     *
     * <p>This example shows how to tune the processing:
     * <ul>
     *   <li>Custom parallelism level (how many actors process concurrently)</li>
     *   <li>Custom timeout for each actor ask</li>
     *   <li>Error handling with supervision</li>
     * </ul>
     *
     * @param inputPath Path to the input file
     * @param outputPath Path to the output file
     * @param parallelism Number of concurrent actor calls
     * @param timeout Timeout for each actor call
     * @return A CompletionStage with the result
     */
    public CompletionStage<Long> processFileWithTuning(
            String inputPath, String outputPath, int parallelism, Duration timeout) {
        return actorSystem
                .getOrSpawn(DataProcessorActor.class, "processor-tuned")
                .thenCompose(processor -> FileIO.fromPath(Paths.get(inputPath))
                        .via(Framing.delimiter(ByteString.fromString("\n"), 1024))
                        .map(ByteString::utf8String)
                        .filter(line -> !line.trim().isEmpty())
                        // Process with custom parallelism and timeout
                        .mapAsync(
                                parallelism,
                                line -> processor
                                        .ask(new DataProcessorActor.ProcessData(line))
                                        .withTimeout(timeout)
                                        .execute()
                                        // Recover from failures
                                        .exceptionally(ex -> new DataProcessorActor.ProcessedResult(
                                                line, "ERROR: " + ex.getMessage(), System.currentTimeMillis())))
                        .map(result -> result.getProcessed() + "\n")
                        .map(ByteString::fromString)
                        .runWith(FileIO.toPath(Paths.get(outputPath)), actorSystem.getRaw())
                        .thenApply(ioResult -> ioResult.count()));
    }

    /**
     * Creates a sample input file for testing.
     *
     * @param path Path where to create the file
     * @param lines Number of lines to write
     */
    public void createSampleFile(String path, int lines) throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= lines; i++) {
            content.append("Line ").append(i).append(": Sample data\n");
        }
        Files.writeString(Path.of(path), content.toString());
    }
}
