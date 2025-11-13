package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.core.pubsub.SpringTopicRef;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.springframework.stereotype.Component;

/**
 * Sharded actor that manages a chat room in a distributed cluster using pub/sub.
 *
 * <p>Each chat room is a separate entity identified by a room ID. The actor creates a pub/sub
 * topic for the room and manages user subscriptions. Messages are broadcast to all subscribers
 * through the topic, eliminating the need to maintain actor references.
 *
 * <p>Sharded actor with pub/sub benefits:
 * <ul>
 *   <li>Automatic distribution - rooms are spread across cluster nodes
 *   <li>Location transparency - clients don't need to know which node hosts a room
 *   <li>Scalability - pub/sub handles message distribution efficiently
 *   <li>Decoupled communication - users subscribe/unsubscribe from topics
 *   <li>Message ordering - guaranteed per-room message ordering
 *   <li>Simplified state - no need to maintain user actor references
 * </ul>
 */
@Component
public class ChatRoomActor implements SpringShardedActor<ChatRoomActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "ChatRoomActor");

    /** Base interface for all commands that can be sent to the chat room actor. */
    public interface Command extends JsonSerializable {}

    /** Command to join a chat room. Provides a topic reference for subscription. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final SpringActorRef<UserActor.Command> userRef;

        @JsonCreator
        public JoinRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("userRef") SpringActorRef<UserActor.Command> userRef) {
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
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String roomId = ctx.getEntityId();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(behaviorCtx -> new ChatRoomBehavior(behaviorCtx, roomId))
                .onMessage(JoinRoom.class, ChatRoomBehavior::onJoinRoom)
                .onMessage(LeaveRoom.class, ChatRoomBehavior::onLeaveRoom)
                .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
                .build();
    }

    /**
     * Behavior handler for chat room actor using pub/sub.
     * Creates a topic for the room and publishes events to subscribers.
     */
    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String roomId;
        private final SpringTopicRef<UserActor.Command> roomTopic;
        private final java.util.Map<String, SpringActorRef<UserActor.Command>> connectedUsers = new java.util.HashMap<>();

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, String roomId) {
            this.ctx = ctx;
            this.roomId = roomId;
            // Create a pub/sub topic for this chat room
            this.roomTopic = ctx.createTopic(UserActor.Command.class, "chat-room-" + roomId);
            ctx.getLog().info("Created pub/sub topic for chat room: {}", roomId);
        }

        /**
         * Handles JoinRoom commands by subscribing the user to the room topic.
         *
         * @param msg The JoinRoom message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onJoinRoom(JoinRoom msg) {
            // Track the user ref for unsubscription
            connectedUsers.put(msg.userId, msg.userRef);

            // Subscribe the user to the room topic
            roomTopic.subscribe(msg.userRef);

            ctx.getLog().info("User {} joined room {} (now {} users)",
                msg.userId, roomId, connectedUsers.size());

            // Notify all users that a new user has joined
            UserActor.JoinRoomEvent joinRoomEvent = new UserActor.JoinRoomEvent(msg.userId);
            roomTopic.publish(joinRoomEvent);

            return Behaviors.same();
        }

        /**
         * Handles LeaveRoom commands by unsubscribing the user from the room topic.
         *
         * @param msg The LeaveRoom message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onLeaveRoom(LeaveRoom msg) {
            // Remove the user and get their ref for unsubscription
            SpringActorRef<UserActor.Command> userRef = connectedUsers.remove(msg.userId);

            if (userRef != null) {
                // Unsubscribe the user from the topic
                roomTopic.unsubscribe(userRef);

                ctx.getLog().info("User {} left room {} ({} users remaining)",
                    msg.userId, roomId, connectedUsers.size());

                // Notify all remaining users that a user has left
                UserActor.LeaveRoomEvent leaveRoomEvent = new UserActor.LeaveRoomEvent(msg.userId);
                roomTopic.publish(leaveRoomEvent);
            }

            return Behaviors.same();
        }

        /**
         * Handles SendMessage commands by publishing the message to the room topic.
         *
         * @param msg The SendMessage message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onSendMessage(SendMessage msg) {
            ctx.getLog().debug("Broadcasting message from {} in room {}", msg.userId, roomId);

            // Create a message event
            UserActor.SendMessageEvent messageEvent = new UserActor.SendMessageEvent(msg.userId, msg.message);

            // Publish the message to all subscribers via the topic
            roomTopic.publish(messageEvent);

            return Behaviors.same();
        }
    }
}
