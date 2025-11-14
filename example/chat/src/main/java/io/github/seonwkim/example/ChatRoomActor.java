package io.github.seonwkim.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringBehaviorContext;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import io.github.seonwkim.core.shard.SpringShardedActorContext;
import io.github.seonwkim.core.topic.SpringTopicManager;
import io.github.seonwkim.core.topic.SpringTopicRef;
import org.apache.pekko.actor.typed.ActorRef;
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

    private final SpringTopicManager topicManager;

    public ChatRoomActor(SpringTopicManager topicManager) {
        this.topicManager = topicManager;
    }

    /** Base interface for all commands that can be sent to the chat room actor. */
    public interface Command extends JsonSerializable {}

    /** Command to join a chat room. Provides actor ref for subscription. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final ActorRef<UserActor.Command> userActorRef;

        @JsonCreator
        public JoinRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("userActorRef") ActorRef<UserActor.Command> userActorRef) {
            this.userId = userId;
            this.userActorRef = userActorRef;
        }
    }

    /** Command to leave a chat room. Provides actor ref for unsubscription. */
    public static class LeaveRoom implements Command {
        public final String userId;
        public final ActorRef<UserActor.Command> userActorRef;

        @JsonCreator
        public LeaveRoom(
                @JsonProperty("userId") String userId,
                @JsonProperty("userActorRef") ActorRef<UserActor.Command> userActorRef) {
            this.userId = userId;
            this.userActorRef = userActorRef;
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
        final SpringTopicRef<UserActor.Command> roomTopic = topicManager
                .topic(UserActor.Command.class)
                .withName("chat-room-" + roomId)
                .create();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                .withState(behaviorCtx -> new ChatRoomBehavior(behaviorCtx, roomId, roomTopic))
                .onMessage(JoinRoom.class, ChatRoomBehavior::onJoinRoom)
                .onMessage(LeaveRoom.class, ChatRoomBehavior::onLeaveRoom)
                .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
                .build();
    }

    /**
     * Behavior handler for chat room actor using pub/sub.
     * Creates a topic owned by this actor using SpringTopicManager.
     */
    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String roomId;
        private final SpringTopicRef<UserActor.Command> roomTopic;

        ChatRoomBehavior(
                SpringBehaviorContext<Command> ctx, String roomId, SpringTopicRef<UserActor.Command> roomTopic) {
            this.ctx = ctx;
            this.roomId = roomId;
            this.roomTopic = roomTopic;
            ctx.getLog().info("Created pub/sub topic for chat room: {}", roomId);
        }

        /**
         * Handles JoinRoom commands by subscribing the user to the room topic.
         *
         * @param msg The JoinRoom message
         * @return The next behavior (same in this case)
         */
        private Behavior<Command> onJoinRoom(JoinRoom msg) {
            // Wrap the Pekko ActorRef in SpringActorRef for subscription
            SpringActorRef<UserActor.Command> springUserRef =
                    new SpringActorRef<>(ctx.getUnderlying().getSystem().scheduler(), msg.userActorRef);

            // Subscribe the user to the room topic
            roomTopic.subscribe(springUserRef);

            ctx.getLog().info("User {} joined room {}", msg.userId, roomId);

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
            // Wrap the Pekko ActorRef in SpringActorRef for unsubscription
            SpringActorRef<UserActor.Command> springUserRef =
                    new SpringActorRef<>(ctx.getUnderlying().getSystem().scheduler(), msg.userActorRef);

            // Unsubscribe the user from the topic
            roomTopic.unsubscribe(springUserRef);

            ctx.getLog().info("User {} left room {}", msg.userId, roomId);

            // Notify all remaining users that a user has left
            UserActor.LeaveRoomEvent leaveRoomEvent = new UserActor.LeaveRoomEvent(msg.userId);
            roomTopic.publish(leaveRoomEvent);

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
