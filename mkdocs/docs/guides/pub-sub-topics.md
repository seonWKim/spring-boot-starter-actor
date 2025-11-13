# Pub/Sub Topics

Distributed publish-subscribe messaging for decoupled, scalable actor communication.

## Overview

Pub/sub topics enable one-to-many message distribution without maintaining subscriber lists. Built on Apache Pekko's distributed pub/sub, topics work seamlessly across cluster nodes with fire-and-forget semantics.

**Key features:**

- Distributed across cluster with location transparency
- At-most-once delivery (fire-and-forget)
- Automatic cleanup when subscribers terminate
- Type-safe with compile-time guarantees

**Use topics for:**

- Broadcasting events (chat rooms, notifications)
- Event-driven architectures with multiple consumers
- Real-time data distribution (metrics, logs)

**Don't use topics for:**

- Guaranteed delivery (use Pekko Streams or persistence)
- Request-response patterns (use `ask`)
- Message acknowledgments

## Creating Topics

### From Actor Context

Create topics owned by an actor:

```java
@Component
public class ChatRoomActor implements SpringShardedActor<Command> {

    private static class ChatRoomBehavior {
        private final SpringBehaviorContext<Command> ctx;
        private final SpringTopicRef<ChatMessage> roomTopic;

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, String roomId) {
            this.ctx = ctx;
            // Create topic - stopped when this actor stops
            this.roomTopic = ctx.createTopic(ChatMessage.class, "chat-room-" + roomId);
        }
    }
}
```

Use `getOrCreateTopic()` for idempotent creation:

```java
SpringTopicRef<MyMessage> topic = ctx.getOrCreateTopic(MyMessage.class, "my-topic");
```

### From Actor System

Create topics that persist for the ActorSystem lifetime:

```java
@Service
public class EventBusService {
    private final SpringTopicRef<SystemEvent> eventBus;

    public EventBusService(SpringActorSystem actorSystem) {
        this.eventBus = actorSystem
                .topic(SystemEvent.class)
                .withName("system-events")
                .create();
    }

    public void publishEvent(SystemEvent event) {
        eventBus.publish(event);
    }
}
```

## Topic Identity and Lifecycle

**Topics are identified by name and message type only**, not by creation location:

```java
// In Actor A
SpringTopicRef<ChatMessage> topic1 = ctx.createTopic(ChatMessage.class, "chat");

// In Actor B
SpringTopicRef<ChatMessage> topic2 = ctx.createTopic(ChatMessage.class, "chat");

// topic1 and topic2 are THE SAME topic!
```

**Lifecycle depends on creation:**

- **Actor-owned** (`ctx.createTopic()`): Stops when owning actor stops
- **System-level** (`actorSystem.topic().create()`): Persists for ActorSystem lifetime

## Publishing Messages

Fire-and-forget with at-most-once delivery:

```java
private Behavior<Command> onSendMessage(SendMessage msg) {
    topic.publish(new ChatMessage(msg.userId, msg.text));
    return Behaviors.same();
}
```

Messages must be serializable:

```java
public class ChatMessage implements JsonSerializable {
    public final String userId;
    public final String message;

    @JsonCreator
    public ChatMessage(
            @JsonProperty("userId") String userId,
            @JsonProperty("message") String message) {
        this.userId = userId;
        this.message = message;
    }
}
```

## Subscribing to Topics

Subscribe using `SpringActorRef`:

```java
private static class SubscriberBehavior {
    SubscriberBehavior(SpringBehaviorContext<Command> ctx, SpringTopicRef<ChatMessage> topic) {
        // Subscribe to topic
        topic.subscribe(ctx.getSelf());
    }

    private Behavior<Command> onChatMessage(ChatMessage msg) {
        // Handle message
        return Behaviors.same();
    }
}
```

Unsubscription is automatic when actor terminates. Manual unsubscription is optional:

```java
topic.unsubscribe(ctx.getSelf());
```

## Cluster Serialization

**Critical**: Use raw Pekko `ActorRef` in cluster messages, not `SpringActorRef`.

**Problem**: `SpringActorRef` contains non-serializable `Scheduler`:

```java
// ❌ BROKEN: SpringActorRef won't serialize across cluster
public static class JoinRoom implements Command {
    public final SpringActorRef<UserCommand> userRef;  // Won't work!
}
```

**Solution**: Use raw `ActorRef` in messages, wrap locally:

```java
// ✅ CORRECT: Raw ActorRef is serializable
public static class JoinRoom implements Command {
    public final org.apache.pekko.actor.typed.ActorRef<UserCommand> userActorRef;

    @JsonCreator
    public JoinRoom(@JsonProperty("userActorRef")
                    org.apache.pekko.actor.typed.ActorRef<UserCommand> userActorRef) {
        this.userActorRef = userActorRef;
    }
}

// Wrap when subscribing
private Behavior<Command> onJoinRoom(JoinRoom msg) {
    SpringActorRef<UserCommand> springRef =
        new SpringActorRef<>(ctx.getScheduler(), msg.userActorRef);
    topic.subscribe(springRef);
    return Behaviors.same();
}

// Send raw ActorRef
roomActor.tell(new JoinRoom(ctx.getUnderlying().getSelf()));
```

## Complete Example

Notification system with pub/sub:

```java
// Message
public class Notification implements JsonSerializable {
    public final String type;
    public final String message;

    @JsonCreator
    public Notification(@JsonProperty("type") String type,
                        @JsonProperty("message") String message) {
        this.type = type;
        this.message = message;
    }
}

// Publisher
@Component
public class NotificationPublisher implements SpringActor<Command> {

    private static class PublisherBehavior {
        private final SpringTopicRef<Notification> topic;

        PublisherBehavior(SpringBehaviorContext<Command> ctx) {
            this.topic = ctx.createTopic(Notification.class, "notifications");
        }

        private Behavior<Command> onPublish(Publish msg) {
            topic.publish(msg.notification);
            return Behaviors.same();
        }
    }
}

// Subscriber
@Component
public class NotificationSubscriber implements SpringActor<Notification> {

    private static class SubscriberBehavior {
        SubscriberBehavior(SpringBehaviorContext<Notification> ctx) {
            SpringTopicRef<Notification> topic =
                ctx.getOrCreateTopic(Notification.class, "notifications");
            topic.subscribe(ctx.getSelf());
        }

        private Behavior<Notification> onNotification(Notification notification) {
            // Process notification
            return Behaviors.same();
        }
    }
}
```

## Best Practices

**Topic naming**: Use descriptive, unique names

```java
// ✅ Good
ctx.createTopic(ChatMessage.class, "chat-room-" + roomId)

// ❌ Bad: Too generic
ctx.createTopic(Message.class, "topic")
```

**Message design**: Keep immutable and serializable

```java
// ✅ Good
public class UserEvent implements JsonSerializable {
    public final String userId;
    public final EventType type;
}

// ❌ Bad: Mutable
public class UserEvent {
    public String userId;  // Mutable!
}
```

**Error handling**: Add application-level acknowledgments for critical messages

```java
private Behavior<Command> onCriticalMessage(CriticalMessage msg) {
    processMessage(msg);
    if (msg.replyTo != null) {
        msg.replyTo.tell(new Acknowledged(msg.id));
    }
    return Behaviors.same();
}
```

## Performance Considerations

**Topic cardinality**: Dozens to hundreds recommended; thousands acceptable with monitoring

**Subscriber count**: Handles hundreds efficiently. For thousands, consider hierarchical topics or Pekko Streams

**Message size**: Keep under 1MB. Use references (URIs, keys) for large payloads

## Troubleshooting

**Messages not received:**

1. Verify `subscribe()` was called
2. Check topic and subscriber use same message type
3. Verify message implements `JsonSerializable`
4. Check cluster logs for serialization errors

**Cluster serialization error:**

```
InvalidDefinitionException: Cannot serialize SpringActorRef
```

Solution - use raw `ActorRef`:

```java
// Change from:
new JoinRoom(userId, ctx.getSelf())  // SpringActorRef

// To:
new JoinRoom(userId, ctx.getUnderlying().getSelf())  // Raw ActorRef
```

## Further Reading

- [Chat Example](../examples/chat.md) - Real-world pub/sub implementation
- [Sharded Actors](sharded-actors.md) - Combining sharding with pub/sub
- [Apache Pekko Documentation](https://pekko.apache.org/docs/pekko/current/typed/distributed-pub-sub.html)
