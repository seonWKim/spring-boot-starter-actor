package io.github.seonwkim.example.streams;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application demonstrating Pekko Streams integration with Spring actors.
 *
 * <p>This application provides various examples showing how to use Pekko Streams
 * with the Spring Boot Actor system. Examples include:
 * <ul>
 *   <li>File processing pipelines with actors</li>
 *   <li>Data transformation pipelines</li>
 *   <li>Actors as stream sources and sinks</li>
 *   <li>Backpressure handling</li>
 *   <li>Throttling and rate limiting</li>
 * </ul>
 *
 * <p>These examples demonstrate integration patterns without reimplementing Pekko Streams,
 * leveraging its built-in features for production-ready stream processing.
 */
@SpringBootApplication
@EnableActorSupport
public class StreamsExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamsExampleApplication.class, args);
    }
}
