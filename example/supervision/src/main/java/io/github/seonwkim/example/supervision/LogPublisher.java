package io.github.seonwkim.example.supervision;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Service for publishing actor logs to connected clients via Server-Sent Events.
 */
@Service
public class LogPublisher {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Register a new SSE emitter for log streaming.
     */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        emitters.add(emitter);
        return emitter;
    }

    /**
     * Publish a log message to all connected clients.
     */
    public void publish(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String logEntry = String.format("[%s] %s", timestamp, message);

        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("log").data(logEntry));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        });
    }
}
