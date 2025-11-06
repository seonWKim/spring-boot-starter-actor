package io.github.seonwkim.example.logging;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NotificationActor demonstrates actor tags and MDC for categorization.
 * This actor is tagged as "notification", "low-priority", and "io-bound"
 * to help filter and monitor notification processing in logs.
 */
@Component
public class NotificationActor implements SpringActor<NotificationActor.Command> {

    public interface Command {}

    public static class SendNotification implements Command {
        public final String notificationId;
        public final String userId;
        public final String type; // email, sms, push
        public final String message;
        public final String requestId;
        public final ActorRef<NotificationSent> replyTo;

        public SendNotification(String notificationId, String userId, String type,
                               String message, String requestId, ActorRef<NotificationSent> replyTo) {
            this.notificationId = notificationId;
            this.userId = userId;
            this.type = type;
            this.message = message;
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
    }

    public static class NotificationSent {
        public final String notificationId;
        public final String status;
        public final String message;

        public NotificationSent(String notificationId, String status, String message) {
            this.notificationId = notificationId;
            this.status = status;
            this.message = message;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
            // Dynamic MDC for notification details
            .withMdc(msg -> {
                if (msg instanceof SendNotification) {
                    SendNotification notification = (SendNotification) msg;
                    Map<String, String> mdc = new java.util.HashMap<>();
                    mdc.put("notificationId", notification.notificationId);
                    mdc.put("userId", notification.userId);
                    mdc.put("notificationType", notification.type);
                    if (notification.requestId != null) {
                        mdc.put("requestId", notification.requestId);
                    }
                    return mdc;
                }
                return Map.of();
            })
            .onMessage(SendNotification.class, (ctx, msg) -> {
                // Tags (notification, low-priority, io-bound) appear in pekkoTags MDC
                ctx.getLog().info("Sending {} notification", msg.type);

                try {
                    // Simulate notification sending
                    ctx.getLog().debug("Preparing notification content");
                    Thread.sleep(50);

                    switch (msg.type.toLowerCase()) {
                        case "email":
                            ctx.getLog().debug("Connecting to email service");
                            Thread.sleep(100);
                            ctx.getLog().info("Email sent successfully");
                            break;
                        case "sms":
                            ctx.getLog().debug("Connecting to SMS gateway");
                            Thread.sleep(80);
                            ctx.getLog().info("SMS sent successfully");
                            break;
                        case "push":
                            ctx.getLog().debug("Connecting to push notification service");
                            Thread.sleep(60);
                            ctx.getLog().info("Push notification sent successfully");
                            break;
                        default:
                            ctx.getLog().warn("Unknown notification type: {}", msg.type);
                    }

                    msg.replyTo.tell(new NotificationSent(
                        msg.notificationId,
                        "SUCCESS",
                        "Notification sent successfully"
                    ));

                } catch (Exception e) {
                    ctx.getLog().error("Failed to send notification", e);
                    msg.replyTo.tell(new NotificationSent(
                        msg.notificationId,
                        "ERROR",
                        "Failed to send notification: " + e.getMessage()
                    ));
                }

                return Behaviors.same();
            })
            .build();
    }
}
