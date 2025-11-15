# Pub/Sub Topics

Pub/Sub topics enable one-to-many messaging where publishers broadcast messages to multiple subscribers without knowing who they are.

## Overview

**Key Features:**

- Distributed across cluster nodes
- Decoupled publishers and subscribers
- Automatic subscriber management
- Works in both local and cluster modes

**Use Cases:**

- Broadcasting events (chat rooms, notifications)
- Real-time updates to multiple actors
- Event-driven architectures

**Not Suitable For:**

- Point-to-point messaging (use direct actor references)
- Guaranteed delivery (topics are at-most-once)
- Request-response patterns (use ask)

## Creating Topics

Inject `SpringTopicManager` and use it to create topics:

```java
@Service
public class ChatService {
    private final SpringTopicManager topicManager;

    public ChatService(SpringTopicManager topicManager) {
        this.topicManager = topicManager;
    }

    public SpringTopicRef<ChatMessage> getChatRoomTopic(String roomId) {
        return topicManager
            .topic(ChatMessage.class)
            .withName("chat-room-" + roomId)
            .getOrCreate();  // Idempotent - safe to call multiple times
    }
}
```

**Methods:**

- `getOrCreate()` - **Recommended**. Creates if doesn't exist, returns existing if it does
- `create()` - Throws exception if topic already exists

!!! tip "Idempotent Creation"
    Always prefer `getOrCreate()` over `create()` to make your code idempotent and avoid exceptions when the topic already exists.

**Message Requirements:**

Messages must implement `JsonSerializable` or `CborSerializable`:

```java
public static class ChatMessage implements JsonSerializable {
    public final String userId;
    public final String content;

    @JsonCreator
    public ChatMessage(
        @JsonProperty("userId") String userId,
        @JsonProperty("content") String content) {
        this.userId = userId;
        this.content = content;
    }
}
```

## Publishing Messages

```java
SpringTopicRef<ChatMessage> topic = topicManager
    .topic(ChatMessage.class)
    .withName("chat-room-lobby")
    .getOrCreate();

topic.publish(new ChatMessage("user123", "Hello everyone!"));
```

**Notes:**

- Fire-and-forget, at-most-once delivery
- Safe to publish even if no subscribers exist

!!! warning "At-Most-Once Delivery"
    Pub/sub topics provide at-most-once delivery semantics. Messages are not guaranteed to be delivered if subscribers are temporarily unavailable.

## Subscribing to Topics

```java
@Service
public class ChatService {
    private final SpringTopicManager topicManager;
    private final SpringActorSystem actorSystem;

    public CompletionStage<Void> joinChatRoom(String userId, String roomId) {
        SpringTopicRef<ChatMessage> roomTopic = topicManager
            .topic(ChatMessage.class)
            .withName("chat-room-" + roomId)
            .getOrCreate();

        return actorSystem
            .getOrSpawn(UserActor.class, "user-" + userId)
            .thenAccept(userActor -> roomTopic.subscribe(userActor));
    }

    public CompletionStage<Void> leaveChatRoom(String userId, String roomId) {
        SpringTopicRef<ChatMessage> roomTopic = topicManager
            .topic(ChatMessage.class)
            .withName("chat-room-" + roomId)
            .getOrCreate();

        return actorSystem
            .get(UserActor.class, "user-" + userId)
            .thenAccept(userActor -> {
                if (userActor != null) roomTopic.unsubscribe(userActor);
            });
    }
}
```

**Notes:**

- Actors must handle the message type published to the topic
- Actors are automatically unsubscribed when they terminate
- Duplicate subscriptions are deduplicated

## Usage Patterns

### Pattern 1: Service-Managed Topics

Cache topic references in services for frequently used topics:

```java
@Service
public class NotificationService {
    private final SpringTopicRef<Notification> notificationTopic;

    public NotificationService(SpringTopicManager topicManager) {
        this.notificationTopic = topicManager
            .topic(Notification.class)
            .withName("system-notifications")
            .getOrCreate();
    }

    public void broadcast(Notification notification) {
        notificationTopic.publish(notification);
    }
}
```

### Pattern 2: Actor-Managed Topics

Actors can inject `SpringTopicManager` to manage their own topics:

```java
@Component
public class ChatRoomActor implements SpringShardedActor<Command> {
    private final SpringTopicManager topicManager;

    public ChatRoomActor(SpringTopicManager topicManager) {
        this.topicManager = topicManager;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        String roomId = ctx.getEntityId();

        SpringTopicRef<ChatEvent> roomTopic = topicManager
            .topic(ChatEvent.class)
            .withName("chat-room-" + roomId)
            .getOrCreate();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
            .withState(behaviorCtx -> new ChatRoomBehavior(behaviorCtx, roomTopic))
            .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
            .build();
    }

    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringTopicRef<ChatEvent> roomTopic;

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, SpringTopicRef<ChatEvent> roomTopic) {
            this.ctx = ctx;
            this.roomTopic = roomTopic;
        }

        private Behavior<Command> onSendMessage(SendMessage msg) {
            roomTopic.publish(new ChatEvent(msg.userId, msg.message));
            return Behaviors.same();
        }
    }
}
```

## Best Practices

1. **Use `getOrCreate()`** for idempotent topic creation
2. **Cache topic references** in service fields
3. **Use descriptive, hierarchical names** (e.g., `chat-room-lobby`, `notifications-user-123`)
4. **Keep messages immutable and small**
5. **Use `JsonSerializable` or `CborSerializable`** for cluster compatibility
6. **Don't rely on topics for critical guaranteed-delivery messages**

!!! tip "Topic Naming"
    Use hierarchical naming conventions like `service-domain-entity` to organize topics logically and make debugging easier.

## Next Steps

- [Sharded Actors](sharded-actors.md) - Distributed entity management in clusters
- [Chat Example](../examples/chat.md) - Real-world pub/sub usage in a chat application
- [Actor Registration](actor-registration-messaging.md) - Core actor concepts and messaging patterns
