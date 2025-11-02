package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

/**
 * Actor that manages a chat room. Each chat room is a separate entity identified by a room ID. The
 * actor maintains a list of connected users and broadcasts messages to all users in the room.
 */
@Component
public class ChatRoomActor implements SpringShardedActor<ChatRoomActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "ChatRoomActor");

    /** Base interface for all commands that can be sent to the chat room actor. */
    public interface Command extends JsonSerializable {}

    /** Command to join a chat room. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final ActorRef<UserActor.Command> userRef;

        @JsonCreator
        public JoinRoom(
                @JsonProperty("userId") String userId, @JsonProperty("userRef") ActorRef<UserActor.Command> userRef) {
            this.userId = userId;
            this.userRef = userRef;
        }
    }

    /** Command to leave a chat room. */
    public static class LeaveRoom implements Command {
        public final String userId;

        @JsonCreator
        public LeaveRoom(@JsonProperty("userId") String userId) {
            this.userId = userId;
        }
    }

    /** Command to send a message to the chat room. */
    public static class SendMessage implements Command {
        public final String userId;
        public final String message;

        @JsonCreator
        public SendMessage(@JsonProperty("userId") String userId, @JsonProperty("message") String message) {
            this.userId = userId;
            this.message = message;
        }
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(EntityContext<Command> ctx) {
        final String roomId = ctx.getEntityId();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .onCreate(actorCtx -> new ChatRoomBehavior(actorCtx, roomId))
                .onMessage(JoinRoom.class, ChatRoomBehavior::onJoinRoom)
                .onMessage(LeaveRoom.class, ChatRoomBehavior::onLeaveRoom)
                .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
                .build();
    }

    /**
     * Behavior handler for chat room actor. Maintains the state of connected users
     * and handles room operations.
     */
    private static class ChatRoomBehavior {
        private final ActorContext<Command> ctx;
        private final String roomId;
        private final Map<String, ActorRef<UserActor.Command>> connectedUsers = new HashMap<>();

        ChatRoomBehavior(ActorContext<Command> ctx, String roomId) {
            this.ctx = ctx;
            this.roomId = roomId;
        }

        /**
         * Handles JoinRoom commands by adding the user to the room and notifying all users.
         *
         * @param msg The JoinRoom message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onJoinRoom(JoinRoom msg) {
            // Add the user to the connected users
            connectedUsers.put(msg.userId, msg.userRef);

            // Notify all users that a new user has joined
            UserActor.JoinRoomEvent joinRoomEvent = new UserActor.JoinRoomEvent(msg.userId);
            broadcastCommand(joinRoomEvent);

            return Behaviors.same();
        }

        /**
         * Handles LeaveRoom commands by removing the user and notifying remaining users.
         *
         * @param msg The LeaveRoom message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onLeaveRoom(LeaveRoom msg) {
            // Remove the user from connected users
            ActorRef<UserActor.Command> userRef = connectedUsers.remove(msg.userId);

            if (userRef != null) {
                // Notify the user that they left the room
                UserActor.LeaveRoomEvent leaveRoomEvent = new UserActor.LeaveRoomEvent(msg.userId);
                userRef.tell(leaveRoomEvent);

                // Notify all remaining users that a user has left
                broadcastCommand(leaveRoomEvent);
            }

            return Behaviors.same();
        }

        /**
         * Handles SendMessage commands by broadcasting the message to all connected users.
         *
         * @param msg The SendMessage message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onSendMessage(SendMessage msg) {
            // Create a message received command
            UserActor.SendMessageEvent receiveMessageCmd = new UserActor.SendMessageEvent(msg.userId, msg.message);

            // Broadcast the message to all connected users
            broadcastCommand(receiveMessageCmd);

            return Behaviors.same();
        }

        /**
         * Broadcasts a command to all connected users.
         *
         * @param command The command to broadcast
         */
        private void broadcastCommand(UserActor.Command command) {
            connectedUsers.values().forEach(userRef -> userRef.tell(command));
        }
    }
}
