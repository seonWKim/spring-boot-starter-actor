package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.serialization.JsonSerializable;

import java.io.IOException;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Actor that represents a user in the chat system. It receives commands and forwards
 * them to the user's WebSocket session.
 */
@Component
public class UserActor implements SpringActor {

    private final ObjectMapper objectMapper;

    @Autowired
    public UserActor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Base interface for all commands that can be sent to the user actor. */
    public interface Command extends JsonSerializable {}

    /** Command sent when a user joins a room. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final String roomId;

        @JsonCreator
        public JoinRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("roomId") String roomId) {
            this.userId = userId;
            this.roomId = roomId;
        }
    }

    /** Command sent when a user leaves a room. */
    public static class LeaveRoom implements Command {
        public final String userId;
        public final String roomId;

        @JsonCreator
        public LeaveRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("roomId") String roomId) {
            this.userId = userId;
            this.roomId = roomId;
        }
    }

    /** Command sent when a message is sent to a room. */
    public static class SendMessage implements Command {
        public final String userId;
        public final String message;
        public final String roomId;

        @JsonCreator
        public SendMessage(
                @JsonProperty("userId") String userId,
                @JsonProperty("message") String message,
                @JsonProperty("roomId") String roomId) {
            this.userId = userId;
            this.message = message;
            this.roomId = roomId;
        }
    }

    /** Command sent when a message is received from a room. */
    public static class ReceiveMessage implements Command {
        public final String userId;
        public final String message;
        public final String roomId;

        @JsonCreator
        public ReceiveMessage(
                @JsonProperty("userId") String userId,
                @JsonProperty("message") String message,
                @JsonProperty("roomId") String roomId) {
            this.userId = userId;
            this.message = message;
            this.roomId = roomId;
        }
    }

    @Override
    public Class<?> commandClass() {
        return Command.class;
    }

    /**
     * Creates a behavior for a user actor. This method is called by the actor system when a new user
     * actor is created.
     *
     * @param actorContext The context of the actor
     * @return A behavior for the actor
     */
    @Override
    public Behavior<Command> create(io.github.seonwkim.core.SpringActorContext actorContext) {
        if (!(actorContext instanceof UserActorContext)) {
            throw new IllegalArgumentException("Expected UserActorContext but got " + actorContext.getClass().getName());
        }

        UserActorContext userActorContext = (UserActorContext) actorContext;
        final String id = userActorContext.actorId();
        final WebSocketSession session = userActorContext.getSession();

        return Behaviors.setup(
                context -> {
                    context.getLog().info("Creating user actor with ID: {}", id);

                    if (session == null) {
                        context.getLog().error("Session not found for user ID: {}", id);
                        return Behaviors.empty();
                    }

                    return new UserActorBehavior(context, session, objectMapper).create();
                });
    }

    // Inner class to isolate stateful behavior logic
    private static class UserActorBehavior {
        private final ActorContext<Command> context;
        private final WebSocketSession session;
        private final ObjectMapper objectMapper;

        UserActorBehavior(
                ActorContext<Command> context,
                WebSocketSession session,
                ObjectMapper objectMapper) {
            this.context = context;
            this.session = session;
            this.objectMapper = objectMapper;
        }

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                    .onMessage(JoinRoom.class, this::onJoinRoom)
                    .onMessage(LeaveRoom.class, this::onLeaveRoom)
                    .onMessage(SendMessage.class, this::onSendMessage)
                    .onMessage(ReceiveMessage.class, this::onReceiveMessage)
                    .build();
        }

        private Behavior<Command> onJoinRoom(JoinRoom command) {
            sendEvent(
                    "user_joined",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoom(LeaveRoom command) {
            sendEvent(
                    "user_left",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onSendMessage(SendMessage command) {
            // This is a command from the user to send a message to the room
            // We don't need to send anything to the WebSocket here
            return Behaviors.same();
        }

        private Behavior<Command> onReceiveMessage(ReceiveMessage command) {
            sendEvent(
                    "message",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("message", command.message);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
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
    }

    @FunctionalInterface
    private interface EventBuilder {
        void build(ObjectNode node);
    }
}
