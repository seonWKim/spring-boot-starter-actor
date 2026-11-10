package io.github.seonwkim.example;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import io.github.seonwkim.actor.topic.SpringTopicManager;
import io.github.seonwkim.actor.topic.SpringTopicRef;
import io.github.seonwkim.actor.topic.TopicName;
import java.util.ArrayList;
import java.util.List;
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

    public static final ShardedEntityType<Command> TYPE_KEY = ShardedEntityType.of(Command.class, "ChatRoomActor");

    private final SpringTopicManager topicManager;

    public ChatRoomActor(SpringTopicManager topicManager) {
        this.topicManager = topicManager;
    }

    /** Base interface for all commands that can be sent to the chat room actor. */
    public interface Command extends JsonSerializable {}

    /** Command to join a chat room. Provides actor ref for subscription. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final SpringActorRef<UserActor.Command> userActorRef;

        public JoinRoom(String userId, SpringActorRef<UserActor.Command> userActorRef) {
            this.userId = userId;
            this.userActorRef = userActorRef;
        }
    }

    /** Command to leave a chat room. Provides actor ref for unsubscription. */
    public static class LeaveRoom implements Command {
        public final String userId;
        public final SpringActorRef<UserActor.Command> userActorRef;

        public LeaveRoom(String userId, SpringActorRef<UserActor.Command> userActorRef) {
            this.userId = userId;
            this.userActorRef = userActorRef;
        }
    }

    /** Command to send a message to the chat room. */
    public static class SendMessage implements Command {
        public final String userId;
        public final String message;

        public SendMessage(String userId, String message) {
            this.userId = userId;
            this.message = message;
        }
    }

    /** The room's topic, sent by the actor to itself once it exists. Local only: it never crosses nodes. */
    private record TopicReady(SpringTopicRef<UserActor.Command> topic, Throwable error) implements Command {}

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String roomId = ctx.getEntityId();

        return SpringActorBehavior.builder(Command.class, ctx)
                .withState(behaviorCtx -> {
                    // Don't block the actor's thread on the topic: ask for it, and get it as a message.
                    behaviorCtx.pipeToSelf(
                            topicManager
                                    .topic(TopicName.of(UserActor.Command.class, "chat-room-" + roomId))
                                    .getOrCreateAsync(),
                            TopicReady::new);
                    return new ChatRoomBehavior(behaviorCtx, roomId);
                })
                .onMessage(TopicReady.class, ChatRoomBehavior::onTopicReady)
                .onMessage(JoinRoom.class, ChatRoomBehavior::onJoinRoom)
                .onMessage(LeaveRoom.class, ChatRoomBehavior::onLeaveRoom)
                .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
                .build();
    }

    /**
     * Behavior handler for chat room actor using pub/sub. Commands that arrive before the room's
     * topic exists wait in {@code pending} and run, in order, once it does.
     */
    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String roomId;
        private final List<Command> pending = new ArrayList<>();
        private SpringTopicRef<UserActor.Command> roomTopic;

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, String roomId) {
            this.ctx = ctx;
            this.roomId = roomId;
        }

        private SpringActorBehavior<Command> onTopicReady(TopicReady ready) {
            if (ready.error() != null) {
                // The room stops; sharding starts it again on its next message, which asks again.
                throw new IllegalStateException("No topic for chat room " + roomId, ready.error());
            }
            roomTopic = ready.topic();
            ctx.getLog().info("Created pub/sub topic for chat room: {}", roomId);
            for (Command command : pending) {
                if (command instanceof JoinRoom join) {
                    onJoinRoom(join);
                } else if (command instanceof LeaveRoom leave) {
                    onLeaveRoom(leave);
                } else if (command instanceof SendMessage send) {
                    onSendMessage(send);
                }
            }
            pending.clear();
            return SpringActorBehavior.same();
        }

        /** True when the command must wait for the topic; it is kept and run later. */
        private boolean waitForTopic(Command command) {
            if (roomTopic == null) {
                pending.add(command);
                return true;
            }
            return false;
        }

        /**
         * Handles JoinRoom commands by subscribing the user to the room topic.
         *
         * @param msg The JoinRoom message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onJoinRoom(JoinRoom msg) {
            if (waitForTopic(msg)) {
                return SpringActorBehavior.same();
            }

            // Subscribe the user to the room topic
            roomTopic.subscribe(msg.userActorRef);

            ctx.getLog().info("User {} joined room {}", msg.userId, roomId);

            // Notify all users that a new user has joined
            UserActor.JoinRoomEvent joinRoomEvent = new UserActor.JoinRoomEvent(msg.userId);
            roomTopic.publish(joinRoomEvent);

            return SpringActorBehavior.same();
        }

        /**
         * Handles LeaveRoom commands by unsubscribing the user from the room topic.
         *
         * @param msg The LeaveRoom message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onLeaveRoom(LeaveRoom msg) {
            if (waitForTopic(msg)) {
                return SpringActorBehavior.same();
            }

            // Unsubscribe the user from the topic
            roomTopic.unsubscribe(msg.userActorRef);

            ctx.getLog().info("User {} left room {}", msg.userId, roomId);

            // Notify all remaining users that a user has left
            UserActor.LeaveRoomEvent leaveRoomEvent = new UserActor.LeaveRoomEvent(msg.userId);
            roomTopic.publish(leaveRoomEvent);

            return SpringActorBehavior.same();
        }

        /**
         * Handles SendMessage commands by publishing the message to the room topic.
         *
         * @param msg The SendMessage message
         * @return The next behavior (same in this case)
         */
        private SpringActorBehavior<Command> onSendMessage(SendMessage msg) {
            if (waitForTopic(msg)) {
                return SpringActorBehavior.same();
            }
            ctx.getLog().debug("Broadcasting message from {} in room {}", msg.userId, roomId);

            // Create a message event
            UserActor.SendMessageEvent messageEvent = new UserActor.SendMessageEvent(msg.userId, msg.message);

            // Publish the message to all subscribers via the topic
            roomTopic.publish(messageEvent);

            return SpringActorBehavior.same();
        }
    }
}
