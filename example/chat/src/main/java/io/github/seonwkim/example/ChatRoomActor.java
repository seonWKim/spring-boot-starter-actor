package io.github.seonwkim.example;

import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.ShardedActor;

/**
 * Actor that manages a chat room.
 * Each chat room is a separate entity identified by a room ID.
 * The actor maintains a list of connected users and broadcasts messages to all users in the room.
 */
@Component
public class ChatRoomActor implements ShardedActor<ChatRoomActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "ChatRoomActor");

    /**
     * Base interface for all commands that can be sent to the chat room actor.
     */
    public interface Command extends JsonSerializable {}

    /**
     * Command to join a chat room.
     */
    public static class JoinRoom implements Command {
        public final String userId;
        public final ActorRef<ChatEvent> userRef;

        @JsonCreator
        public JoinRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("userRef") ActorRef<ChatEvent> userRef) {
            this.userId = userId;
            this.userRef = userRef;
        }
    }

    /**
     * Command to leave a chat room.
     */
    public static class LeaveRoom implements Command {
        public final String userId;

        @JsonCreator
        public LeaveRoom(@JsonProperty("userId") String userId) {
            this.userId = userId;
        }
    }

    /**
     * Command to send a message to the chat room.
     */
    public static class SendMessage implements Command {
        public final String userId;
        public final String message;

        @JsonCreator
        public SendMessage(
                @JsonProperty("userId") String userId,
                @JsonProperty("message") String message
        ) {
            this.userId = userId;
            this.message = message;
        }
    }

    /**
     * Base interface for all events that can be sent from the chat room actor to clients.
     */
    public interface ChatEvent extends JsonSerializable {}

    /**
     * Event sent when a user joins the room.
     */
    public static class UserJoined implements ChatEvent {
        public final String userId;
        public final String roomId;

        @JsonCreator
        public UserJoined(
                @JsonProperty("userId") String userId,
                @JsonProperty("roomId") String roomId
        ) {
            this.userId = userId;
            this.roomId = roomId;
        }
    }

    /**
     * Event sent when a user leaves the room.
     */
    public static class UserLeft implements ChatEvent {
        public final String userId;
        public final String roomId;

        @JsonCreator
        public UserLeft(
                @JsonProperty("userId") String userId,
                @JsonProperty("roomId") String roomId
        ) {
            this.userId = userId;
            this.roomId = roomId;
        }
    }

    /**
     * Event sent when a message is received in the room.
     */
    public static class MessageReceived implements ChatEvent {
        public final String userId;
        public final String message;
        public final String roomId;

        @JsonCreator
        public MessageReceived(
                @JsonProperty("userId") String userId,
                @JsonProperty("message") String message,
                @JsonProperty("roomId") String roomId) {
            this.userId = userId;
            this.message = message;
            this.roomId = roomId;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(context -> {
            final String roomId = ctx.getEntityId();
            return chatRoom(roomId, new HashMap<>());
        });
    }

    /**
     * Creates the behavior for a chat room with the given room ID and connected users.
     *
     * @param roomId The ID of the chat room
     * @param connectedUsers Map of user IDs to their actor references
     *
     * @return The behavior for the chat room
     */
    private Behavior<Command> chatRoom(String roomId, Map<String, ActorRef<ChatEvent>> connectedUsers) {
        return Behaviors.receive(Command.class)
                        .onMessage(JoinRoom.class, msg -> {
                            // Add the user to the connected users
                            connectedUsers.put(msg.userId, msg.userRef);

                            // Notify all users that a new user has joined
                            UserJoined event = new UserJoined(msg.userId, roomId);
                            broadcastEvent(connectedUsers, event);

                            return chatRoom(roomId, connectedUsers);
                        })
                        .onMessage(LeaveRoom.class, msg -> {
                            // Remove the user from connected users
                            connectedUsers.remove(msg.userId);

                            // Notify all users that a user has left
                            UserLeft event = new UserLeft(msg.userId, roomId);
                            broadcastEvent(connectedUsers, event);

                            return chatRoom(roomId, connectedUsers);
                        })
                        .onMessage(SendMessage.class, msg -> {
                            // Create a message received event
                            MessageReceived event = new MessageReceived(msg.userId, msg.message, roomId);

                            // Broadcast the message to all connected users
                            broadcastEvent(connectedUsers, event);

                            return Behaviors.same();
                        })
                        .build();
    }

    /**
     * Broadcasts an event to all connected users.
     *
     * @param connectedUsers Map of user IDs to their actor references
     * @param event The event to broadcast
     */
    private void broadcastEvent(Map<String, ActorRef<ChatEvent>> connectedUsers, ChatEvent event) {
        connectedUsers.values().forEach(userRef -> userRef.tell(event));
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(3);
    }
}
