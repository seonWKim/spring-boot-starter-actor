package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.MdcConfig;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.TagsConfig;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationService {

    private final SpringActorRef<NotificationActor.Command> notificationActor;

    public NotificationService(SpringActorSystem actorSystem) {
        Map<String, String> staticMdc = Map.of(
                "service", "notification-service",
                "component", "background-worker");

        this.notificationActor = actorSystem
                .actor(NotificationActor.class)
                .withId("notification-actor")
                .withMdc(MdcConfig.of(staticMdc))
                .withTags(TagsConfig.of("notification", "low-priority", "io-bound"))
                .withBlockingDispatcher()
                .withTimeout(Duration.ofSeconds(5))
                .spawnAndWait();
    }

    public Mono<NotificationActor.NotificationSent> sendNotification(String userId, String type, String message) {
        String requestId = MDC.get("requestId");
        return sendNotification(userId, type, message, requestId);
    }

    public Mono<NotificationActor.NotificationSent> sendNotification(
            String userId, String type, String message, String requestId) {
        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8);
        return Mono.fromCompletionStage(notificationActor
                .ask(new NotificationActor.SendNotification(notificationId, userId, type, message, requestId))
                .execute());
    }
}
