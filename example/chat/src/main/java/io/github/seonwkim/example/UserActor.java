package io.github.seonwkim.example;

import java.io.IOException;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import io.github.seonwkim.core.serialization.JsonSerializable;

@Component
public class UserActor implements SpringActor {

    public interface Command extends JsonSerializable {}

    public static class Connect implements Command {
    }

    public static class JoinRoom implements Command {
        private final String roomId;

        public JoinRoom(String roomId) {this.roomId = roomId;}
    }

    public static class LeaveRoom implements Command {
        public LeaveRoom() {}
    }

    public static class SendMessage implements Command {
        private final String message;

        public SendMessage(String message) {this.message = message;}
    }

    public static class JoinRoomEvent implements Command {
        private final String userId;

        public JoinRoomEvent(String userId) {this.userId = userId;}
    }

    public static class LeaveRoomEvent implements Command {
        private final String userId;

        public LeaveRoomEvent(String userId) {this.userId = userId;}
    }

    public static class SendMessageEvent implements Command {
        private final String userId;
        private final String message;

        public SendMessageEvent(String userId, String message) {
            this.userId = userId;
            this.message = message;
        }
    }

    @Override
    public Class<?> commandClass() {
        return UserActor.Command.class;
    }

    public static class UserActorV2Context implements SpringActorContext {
        private final SpringActorSystem actorSystem;
        private final ObjectMapper objectMapper;
        private final WebSocketSession session;

        private final String userId;

        public UserActorV2Context(SpringActorSystem actorSystem, ObjectMapper objectMapper, String userId,
                                  WebSocketSession session) {
            this.actorSystem = actorSystem;
            this.objectMapper = objectMapper;
            this.userId = userId;
            this.session = session;
        }

        @Override
        public String actorId() {
            return userId;
        }
    }

    @Override
    public Behavior<Command> create(SpringActorContext actorContext) {
        if (!(actorContext instanceof UserActorV2Context userActorContext)) {
            throw new IllegalStateException("Must be UserActorV2Context");
        }

        return Behaviors.setup(
                context -> new UserActorV2Behavior(
                        context,
                        userActorContext.actorSystem,
                        userActorContext.objectMapper,
                        userActorContext.userId,
                        userActorContext.session
                ).create()
        );
    }

    public static class UserActorV2Behavior {
        private final ActorContext<UserActor.Command> context;
        private final SpringActorSystem actorSystem;
        private final ObjectMapper objectMapper;

        private final String userId;
        private final WebSocketSession session;

        @Nullable
        private String currentRoomId;

        public UserActorV2Behavior(ActorContext<Command> context, SpringActorSystem actorSystem,
                                   ObjectMapper objectMapper, String userId, WebSocketSession session) {
            this.context = context;
            this.actorSystem = actorSystem;
            this.objectMapper = objectMapper;
            this.userId = userId;
            this.session = session;
        }

        public Behavior<UserActor.Command> create() {
            return Behaviors.receive(Command.class)
                            .onMessage(Connect.class, this::onConnect)
                            .onMessage(JoinRoom.class, this::onJoinRoom)
                            .onMessage(LeaveRoom.class, this::onLeaveRoom)
                            .onMessage(SendMessage.class, this::onSendMessage)
                            .onMessage(JoinRoomEvent.class, this::onJoinRoomEvent)
                            .onMessage(LeaveRoomEvent.class, this::onLeaveRoomEvent)
                            .onMessage(SendMessageEvent.class, this::onSendMessageEvent)
                            .build();
        }

        private Behavior<Command> onConnect(Connect connect) {
            sendEvent(
                    "connected",
                    builder -> {
                        builder.put("userId", userId);
                    });

            return Behaviors.same();
        }

        private Behavior<Command> onJoinRoom(JoinRoom command) {
            currentRoomId = command.roomId;
            final var roomActor = getRoomActor();
            sendEvent(
                    "joined",
                    builder -> {
                        builder.put("roomId", currentRoomId);
                    });

            roomActor.tell(new ChatRoomActor.JoinRoom(userId, context.getSelf()));
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoom(LeaveRoom command) {
            if (currentRoomId == null) {
                context.getLog().info("{} user has not joined any room.", userId);
                return Behaviors.same();
            }

            sendEvent(
                    "left",
                    builder -> {
                        builder.put("roomId", currentRoomId);
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
            sendEvent(
                    "user_joined",
                    builder -> {
                        builder.put("userId", event.userId);
                        builder.put("roomId", currentRoomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoomEvent(LeaveRoomEvent event) {
            sendEvent(
                    "user_left",
                    builder -> {
                        builder.put("userId", event.userId);
                        builder.put("roomId", currentRoomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onSendMessageEvent(SendMessageEvent event) {
            sendEvent(
                    "message",
                    builder -> {
                        builder.put("userId", event.userId);
                        builder.put("message", event.message);
                        builder.put("roomId", currentRoomId);
                    });
            return Behaviors.same();
        }

        private SpringShardedActorRef<ChatRoomActor.Command> getRoomActor() {
            return actorSystem.entityRef(ChatRoomActor.TYPE_KEY, currentRoomId);
        }

        private void sendEvent(String type, EventBuilder builder) {
            try {
                ObjectNode eventNode = objectMapper.createObjectNode();
                eventNode.put("type", type);
                builder.build(eventNode);

                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(eventNode)));
                }
            } catch (IOException e) {
                context.getLog().error("Failed to send message to WebSocket", e);
            }
        }

        @FunctionalInterface
        private interface EventBuilder {
            void build(ObjectNode node);
        }
    }
}
