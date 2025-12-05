# Chat Example

![Chat Demo](../chat.gif)

This guide demonstrates how to build a real-time chat application using Spring Boot Starter Actor's pub/sub topics without introducing third-party middleware.

## Overview

The chat example shows how to:

- Build a real-time chat application using actors and pub/sub topics
- Implement WebSocket communication for real-time messaging
- Create a scalable, clustered chat system using distributed pub/sub
- Eliminate the need for external message brokers or middleware

This example demonstrates how Spring Boot Starter Actor can be used to build real-world applications efficiently without relying on additional infrastructure components.

!!! success "No External Dependencies"
    No Redis, RabbitMQ, or Kafka required! The actor system provides everything needed for distributed messaging.

## Source Code

You can find the complete source code for this example on GitHub:

[Chat Example Source Code](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/chat)

!!! tip "Real-World Application"
    This example demonstrates patterns applicable to many real-time applications beyond chat.

## Key Components

### ChatRoomActor with Pub/Sub

`ChatRoomActor` is a sharded actor that manages a chat room using **pub/sub topics**. Each chat room creates a topic for message distribution, and users subscribe to receive messages. This eliminates the need to maintain a list of user actor references:

```java
@Component
public class ChatRoomActor implements SpringShardedActor<ChatRoomActor.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "ChatRoomActor");

    /** Base interface for all commands that can be sent to the chat room actor. */
    public interface Command extends JsonSerializable {}

    /** Command to join a chat room. Provides actor ref for subscription. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final ActorRef<UserActor.Command> userActorRef;

        public JoinRoom(String userId, ActorRef<UserActor.Command> userActorRef) {
            this.userId = userId;
            this.userActorRef = userActorRef;
        }
    }

   /** Command to leave a chat room. */
   public static class LeaveRoom implements Command {
      public final String userId;

      public LeaveRoom( String userId) {
         this.userId = userId;
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
     *
     * Note: Since this is a sharded actor that doesn't easily support Spring DI,
     * we use TopicSpawner directly. For regular actors, use SpringTopicManager
     * with dependency injection instead.
     */
    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final String roomId;
        private final SpringTopicRef<UserActor.Command> roomTopic;
        private final Map<String, SpringActorRef<UserActor.Command>> connectedUsers =
                new HashMap<>();

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, String roomId) {
            this.ctx = ctx;
            this.roomId = roomId;
            // Create a pub/sub topic for this chat room using TopicSpawner directly
            // For actors with DI support, use SpringTopicManager instead
            this.roomTopic = TopicSpawner.createTopic(
                ctx.getUnderlying(),
                UserActor.Command.class,
                "chat-room-" + roomId
            );
            ctx.getLog().info("Created pub/sub topic for chat room: {}", roomId);
        }

        /**
         * Handles JoinRoom commands by subscribing the user to the room topic.
         */
        private Behavior<Command> onJoinRoom(JoinRoom msg) {
            // Wrap the Pekko ActorRef in SpringActorRef for subscription
            SpringActorRef<UserActor.Command> springUserRef =
                new SpringActorRef<>(
                    ctx.getUnderlying().getSystem().scheduler(),
                    msg.userActorRef);

            // Track the user ref for unsubscription
            connectedUsers.put(msg.userId, springUserRef);

            // Subscribe the user to the room topic
            roomTopic.subscribe(springUserRef);

            ctx.getLog().info("User {} joined room {} (now {} users)",
                msg.userId, roomId, connectedUsers.size());

            // Notify all users that a new user has joined
            UserActor.JoinRoomEvent joinRoomEvent = new UserActor.JoinRoomEvent(msg.userId);
            roomTopic.publish(joinRoomEvent);

            return Behaviors.same();
        }

        /**
         * Handles LeaveRoom commands by unsubscribing the user from the room topic.
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
         */
        private Behavior<Command> onSendMessage(SendMessage msg) {
            ctx.getLog().debug("Broadcasting message from {} in room {}", msg.userId, roomId);

            // Create a message event
            UserActor.SendMessageEvent messageEvent =
                new UserActor.SendMessageEvent(msg.userId, msg.message);

            // Publish the message to all subscribers via the topic
            roomTopic.publish(messageEvent);

            return Behaviors.same();
        }
    }
}
```

### UserActor

`UserActor` represents a connected user. It subscribes to chat room topics and sends messages to the user's WebSocket connection:

```java
@Component
public class UserActor implements SpringActorWithContext<
    UserActor.Command, UserActor.UserActorContext> {

    public interface Command extends JsonSerializable {}

    // Command and event classes...

    @Override
    public SpringActorBehavior<Command> create(UserActorContext actorContext) {
        return SpringActorBehavior.builder(Command.class, actorContext)
                .withState(ctx -> new UserActorBehavior(
                        ctx, actorContext.actorSystem, actorContext.userId,
                        actorContext.messageSink))
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
        private final SpringBehaviorContext<Command> context;
        private final SpringActorSystem actorSystem;
        private final String userId;
        private final Sinks.Many<String> messageSink;

        @Nullable private String currentRoomId;

        // When joining a room, send the raw Pekko ActorRef (serializable)
        private Behavior<Command> onJoinRoom(JoinRoom command) {
            currentRoomId = command.roomId;
            final var roomActor = getRoomActor();
            sendEvent("joined", json -> {
                json.append(",\"roomId\":\"").append(escapeJson(currentRoomId)).append("\"");
            });

            // Send raw ActorRef for cluster serialization
            roomActor.tell(new ChatRoomActor.JoinRoom(
                userId,
                context.getUnderlying().getSelf()));
            return Behaviors.same();
        }

        // Event handlers receive messages via pub/sub topic
        private Behavior<Command> onJoinRoomEvent(JoinRoomEvent event) {
            sendEvent("user_joined", json -> {
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
    }
}
```

## Architecture with Pub/Sub

### Benefits of Pub/Sub Approach

The pub/sub implementation provides several advantages:

1. **Simplified State Management** - No need to maintain a list of user actor references
2. **Automatic Cleanup** - Users are automatically unsubscribed when their actors terminate
3. **Scalability** - Topics work seamlessly across cluster nodes
4. **Decoupled Communication** - Publishers don't need to know about subscribers
5. **Location Transparency** - Works the same whether actors are local or distributed

!!! info "Pub/Sub vs Direct Messaging"
    Pub/sub is ideal for one-to-many communication patterns. For point-to-point messaging, use direct actor references instead.

### Message Flow

```
1. User connects → UserActor created
2. User joins room → UserActor tells ChatRoomActor
3. ChatRoomActor subscribes UserActor to room topic
4. User sends message → ChatRoomActor publishes to topic
5. All subscribed UserActors receive message via topic
6. UserActors forward to WebSocket clients
```

### Cluster Serialization

**Important**: When sending actor references across cluster boundaries, use raw Pekko `ActorRef` instead of `SpringActorRef`:

```java
// ✅ Correct: Raw ActorRef is serializable
roomActor.tell(new ChatRoomActor.JoinRoom(userId, context.getUnderlying().getSelf()));

// ❌ Wrong: SpringActorRef contains non-serializable Scheduler
roomActor.tell(new ChatRoomActor.JoinRoom(userId, context.getSelf()));
```

!!! warning "Serialization"
    `SpringActorRef` is a local convenience wrapper that contains a non-serializable scheduler. For cluster messages, always use the raw Pekko `ActorRef` from `context.getUnderlying().getSelf()`.

## Running the Application

### Local Cluster Setup

You can run multiple instances of the application locally using Gradle:

```bash
# Run the server app 
sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Run the frontend app  
cd example/chat/frontend 
npm run dev 
```

## Architecture Benefits

This architecture eliminates the need for third-party middleware by leveraging:

- **Distributed pub/sub topics** for message distribution
- **Sharded actors** for scalability and fault tolerance
- **Built-in message routing** between actors
- **Natural state management** within actors
- **Real-time communication** via WebSockets
- **Automatic cleanup** and lifecycle management

!!! success "Production Ready"
    This architecture is production-ready and can scale to thousands of concurrent users across multiple nodes.

## Key Takeaways

- Pub/sub topics provide a clean abstraction for one-to-many communication
- The actor model combined with pub/sub creates an efficient, scalable messaging system
- WebSockets and actors work together seamlessly for real-time applications
- No external message broker required - everything is built into the framework
- Cluster-aware topics distribute messages automatically across nodes

## Next Steps

- [Pub/Sub Topics Guide](../guides/pub-sub-topics.md) - Deep dive into pub/sub concepts
- [Cluster Example](cluster.md) - Learn about cluster sharding
