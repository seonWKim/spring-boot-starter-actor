package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringActorWithContext;
import io.github.seonwkim.core.SpringShardedActorRef;
import io.github.seonwkim.core.serialization.JsonSerializable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class UserActor implements SpringActorWithContext<UserActor.Command, UserActor.UserActorContext> {

    public interface Command extends JsonSerializable {}

    public static class Connect implements Command {
        @JsonCreator
        public Connect() {}
    }

    public static class JoinRoom implements Command {
        private final String roomId;

        @JsonCreator
        public JoinRoom(@JsonProperty("roomId") String roomId) {
            this.roomId = roomId;
        }

        public String getRoomId() {
            return roomId;
        }
    }

    public static class LeaveRoom implements Command {
        @JsonCreator
        public LeaveRoom() {}
    }

    public static class SendMessage implements Command {
        private final String message;

        @JsonCreator
        public SendMessage(@JsonProperty("message") String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class JoinRoomEvent implements Command {
        private final String userId;

        @JsonCreator
        public JoinRoomEvent(@JsonProperty("userId") String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    public static class LeaveRoomEvent implements Command {
        private final String userId;

        @JsonCreator
        public LeaveRoomEvent(@JsonProperty("userId") String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    public static class SendMessageEvent implements Command {
        private final String userId;
        private final String message;

        @JsonCreator
        public SendMessageEvent(@JsonProperty("userId") String userId, @JsonProperty("message") String message) {
            this.userId = userId;
            this.message = message;
        }
    }

    public static class UserActorContext extends SpringActorContext {
        private final SpringActorSystem actorSystem;
        private final reactor.core.publisher.Sinks.Many<String> messageSink;

        private final String userId;

        public UserActorContext(
                SpringActorSystem actorSystem, String userId, reactor.core.publisher.Sinks.Many<String> messageSink) {
            this.actorSystem = actorSystem;
            this.userId = userId;
            this.messageSink = messageSink;
        }

        @Override
        public String actorId() {
            return userId;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(UserActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .onCreate(ctx -> new UserActorBehavior(
                        ctx, actorContext.actorSystem, actorContext.userId, actorContext.messageSink))
                .onMessage(Connect.class, UserActorBehavior::onConnect)
                .onMessage(JoinRoom.class, UserActorBehavior::onJoinRoom)
                .onMessage(LeaveRoom.class, UserActorBehavior::onLeaveRoom)
                .onMessage(SendMessage.class, UserActorBehavior::onSendMessage)
                .onMessage(JoinRoomEvent.class, UserActorBehavior::onJoinRoomEvent)
                .onMessage(LeaveRoomEvent.class, UserActorBehavior::onLeaveRoomEvent)
                .onMessage(SendMessageEvent.class, UserActorBehavior::onSendMessageEvent)
                .build();
    }

    public static class UserActorBehavior {
        private final ActorContext<UserActor.Command> context;
        private final SpringActorSystem actorSystem;

        private final String userId;
        private final reactor.core.publisher.Sinks.Many<String> messageSink;

        @Nullable private String currentRoomId;

        public UserActorBehavior(
                ActorContext<Command> context,
                SpringActorSystem actorSystem,
                String userId,
                reactor.core.publisher.Sinks.Many<String> messageSink) {
            this.context = context;
            this.actorSystem = actorSystem;
            this.userId = userId;
            this.messageSink = messageSink;
        }

        private Behavior<Command> onConnect(Connect connect) {
            sendEvent("connected", json -> {
                json.append(",\"userId\":\"").append(escapeJson(userId)).append("\"");
            });

            return Behaviors.same();
        }

        private Behavior<Command> onJoinRoom(JoinRoom command) {
            currentRoomId = command.roomId;
            final var roomActor = getRoomActor();
            sendEvent("joined", json -> {
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });

            roomActor.tell(new ChatRoomActor.JoinRoom(userId, context.getSelf()));
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoom(LeaveRoom command) {
            if (currentRoomId == null) {
                context.getLog().info("{} user has not joined any room.", userId);
                return Behaviors.same();
            }

            sendEvent("left", json -> {
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });

            final var roomActor = getRoomActor();
            roomActor.tell(new ChatRoomActor.LeaveRoom(userId));

            return Behaviors.same();
        }

        private Behavior<Command> onSendMessage(SendMessage command) {
            if (currentRoomId == null) {
                context.getLog().info("{} user has not joined any room.", userId);
                return Behaviors.same();
            }

            final var roomActor = getRoomActor();
            roomActor.tell(new ChatRoomActor.SendMessage(userId, command.message));

            return Behaviors.same();
        }

        private Behavior<Command> onJoinRoomEvent(JoinRoomEvent event) {
            sendEvent("user_joined", json -> {
                json.append(",\"userId\":\"").append(escapeJson(event.userId)).append("\"");
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoomEvent(LeaveRoomEvent event) {
            sendEvent("user_left", json -> {
                json.append(",\"userId\":\"").append(escapeJson(event.userId)).append("\"");
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });
            return Behaviors.same();
        }

        private Behavior<Command> onSendMessageEvent(SendMessageEvent event) {
            sendEvent("message", json -> {
                json.append(",\"userId\":\"").append(escapeJson(event.userId)).append("\"");
                json.append(",\"message\":\"").append(escapeJson(event.message)).append("\"");
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });
            return Behaviors.same();
        }

        private SpringShardedActorRef<ChatRoomActor.Command> getRoomActor() {
            return actorSystem
                    .sharded(ChatRoomActor.class)
                    .withId(currentRoomId)
                    .get();
        }

        private void sendEvent(String type, EventBuilder builder) {
            try {
                // Build JSON string directly to avoid blocking Jackson serialization
                StringBuilder json = new StringBuilder();
                json.append("{\"type\":\"").append(escapeJson(type)).append("\"");

                builder.build(json);

                json.append("}");
                messageSink.tryEmitNext(json.toString());
            } catch (Exception e) {
                context.getLog().error("Failed to send message to WebSocket", e);
            }
        }

        /**
         * Escape JSON string values to prevent injection and ensure valid JSON.
         * This is a simple escaping for common characters.
         */
        private String escapeJson(String value) {
            if (value == null) return "";
            return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        @FunctionalInterface
        private interface EventBuilder {
            void build(StringBuilder json);
        }
    }
}
