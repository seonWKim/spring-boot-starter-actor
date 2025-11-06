package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.MdcConfig;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * NotificationService demonstrates spawning actors with tags and MDC
 * for low-priority background tasks.
 */
@Service
public class NotificationService {

    private final SpringActorSystem actorSystem;
    private final SpringActorRef<NotificationActor.Command> notificationActor;

    public NotificationService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;

        // Spawn NotificationActor with:
        // 1. Static MDC for service identification
        // 2. Tags indicating this is a low-priority, I/O-bound notification service
        Map<String, String> staticMdc = Map.of(
            "service", "notification-service",
            "component", "background-worker"
        );

        this.notificationActor = actorSystem
            .actor(NotificationActor.class)
            .withId("notification-actor")
            .withMdc(MdcConfig.of(staticMdc))
            .withTags(TagsConfig.of("notification", "low-priority", "io-bound"))
            .withBlockingDispatcher() // Use blocking dispatcher for I/O
            .withTimeout(Duration.ofSeconds(5))
            .spawnAndWait();
    }

    /**
     * Send a notification with full context tracking.
     */
    public Mono<NotificationActor.NotificationSent> sendNotification(
            String userId, String type, String message) {

        String requestId = MDC.get("requestId");
        return sendNotification(userId, type, message, requestId);
    }

    /**
     * Send a notification with explicit requestId (for reactive chain calls).
     * Use this when calling from within reactive chains where MDC may not be available.
     */
    public Mono<NotificationActor.NotificationSent> sendNotification(
            String userId, String type, String message, String requestId) {

        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8);

        // Pass requestId to actor so it appears in logs via withMdc()
        return Mono.fromCompletionStage(
            notificationActor.ask(
                replyTo -> new NotificationActor.SendNotification(
                    notificationId, userId, type, message, requestId, replyTo),
                Duration.ofSeconds(10)
            )
        );
    }
}
