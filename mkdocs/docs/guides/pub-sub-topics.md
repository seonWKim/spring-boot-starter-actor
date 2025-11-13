# Pub/Sub Topics

Distributed publish-subscribe messaging for decoupled, scalable actor communication.

## Overview

Pub/sub topics enable one-to-many message distribution without maintaining subscriber lists. Built on Apache Pekko's distributed pub/sub, topics work seamlessly across cluster nodes with fire-and-forget semantics.

**Key features:**

- Distributed across cluster with location transparency
- At-most-once delivery (fire-and-forget)
- Automatic cleanup when subscribers terminate
- Type-safe with compile-time guarantees
- Spring DI integration via `SpringTopicManager`

**Use topics for:**

- Broadcasting events (chat rooms, notifications)
- Event-driven architectures with multiple consumers
- Real-time data distribution (metrics, logs)

**Don't use topics for:**

- Guaranteed delivery (use Pekko Streams or persistence)
- Request-response patterns (use `ask`)
- Message acknowledgments

## Creating Topics

Topics are managed through the `SpringTopicManager` service, which provides a fluent API for creating bounded and unbounded topics.

### Unbounded Topics

Unbounded topics persist for the entire application lifecycle:

```java
@Service
public class EventBusService {
    private final SpringTopicManager topicManager;
    private final SpringTopicRef<SystemEvent> eventBus;

    public EventBusService(SpringTopicManager topicManager) {
        this.topicManager = topicManager;
        // Create unbounded topic - persists for app lifetime
        this.eventBus = topicManager
                .topic(SystemEvent.class)
                .withName("system-events")
                .create();
    }

    public void publishEvent(SystemEvent event) {
        eventBus.publish(event);
    }
}
```

### Bounded Topics

Bounded topics are tied to an actor's lifecycle and automatically stop when the owning actor stops:

```java
@Component
public class ChatRoomActor implements SpringActorWithContext<Command, Context> {

    private static class ChatRoomBehavior {
        private final SpringTopicRef<ChatMessage> roomTopic;

        ChatRoomBehavior(
                SpringBehaviorContext<Command> ctx,
                SpringTopicManager topicManager,
                String roomId) {
            // Create bounded topic - stopped when this actor stops
            this.roomTopic = topicManager
                    .topic(ChatMessage.class)
                    .withName("chat-room-" + roomId)
                    .ownedBy(ctx)
                    .create();
        }
    }

    @Override
    public SpringActorBehavior<Command> create(Context context) {
        // Inject SpringTopicManager via WithContext pattern
        SpringTopicManager topicManager = context.getBean(SpringTopicManager.class);

        return SpringActorBehavior.builder(Command.class, context)
                .withState(ctx -> new ChatRoomBehavior(ctx, topicManager, context.getRoomId()))
                .onMessage(SendMessage.class, ChatRoomBehavior::onSendMessage)
                .build();
    }
}
```

### Using WithContext for DI

The `WithContext` pattern allows actors to access Spring beans like `SpringTopicManager`:

```java
@Component
public class NotificationActor
        implements SpringActorWithContext<Command, NotificationActor.Context> {

    public static class Context extends SpringActorContext {
        private final String actorId;

        public Context(String actorId) {
            this.actorId = actorId;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(Context context) {
        SpringTopicManager topicManager = context.getBean(SpringTopicManager.class);

        return SpringActorBehavior.builder(Command.class, context)
                .withState(ctx -> {
                    SpringTopicRef<Notification> topic = topicManager
                            .topic(Notification.class)
                            .withName("notifications")
                            .ownedBy(ctx)
                            .create();
                    return new NotificationBehavior(ctx, topic);
                })
                .build();
    }
}
```

### Advanced: Using TopicSpawner

For cases where DI is not available (e.g., sharded actors), use `TopicSpawner` directly:

```java
@Component
public class ChatRoomActor implements SpringShardedActor<Command> {

    private static class ChatRoomBehavior {
        private final SpringTopicRef<UserCommand> roomTopic;

        ChatRoomBehavior(SpringBehaviorContext<Command> ctx, String roomId) {
            // Use TopicSpawner directly for sharded actors
            this.roomTopic = TopicSpawner.createTopic(
                ctx.getUnderlying(),
                UserCommand.class,
                "chat-room-" + roomId
            );
        }
    }
}
```

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
    SubscriberBehavior(
            SpringBehaviorContext<Command> ctx,
            SpringTopicRef<ChatMessage> topic) {
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

Notification system with pub/sub using `SpringTopicManager`:

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

// Publisher Actor
@Component
public class NotificationPublisher
        implements SpringActorWithContext<Command, NotificationPublisher.Context> {

    public static class Context extends SpringActorContext {
        private final String actorId;

        public Context(String actorId) {
            this.actorId = actorId;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public SpringActorBehavior<Command> create(Context context) {
        SpringTopicManager topicManager = context.getBean(SpringTopicManager.class);

        return SpringActorBehavior.builder(Command.class, context)
                .withState(ctx -> {
                    SpringTopicRef<Notification> topic = topicManager
                            .topic(Notification.class)
                            .withName("notifications")
                            .ownedBy(ctx)
                            .create();
                    return new PublisherBehavior(topic);
                })
                .onMessage(Publish.class, PublisherBehavior::onPublish)
                .build();
    }

    private static class PublisherBehavior {
        private final SpringTopicRef<Notification> topic;

        PublisherBehavior(SpringTopicRef<Notification> topic) {
            this.topic = topic;
        }

        private Behavior<Command> onPublish(Publish msg) {
            topic.publish(msg.notification);
            return Behaviors.same();
        }
    }
}

// Subscriber Actor
@Component
public class NotificationSubscriber
        implements SpringActorWithContext<Notification, NotificationSubscriber.Context> {

    public static class Context extends SpringActorContext {
        private final String actorId;

        public Context(String actorId) {
            this.actorId = actorId;
        }

        @Override
        public String actorId() {
            return actorId;
        }
    }

    @Override
    public SpringActorBehavior<Notification> create(Context context) {
        SpringTopicManager topicManager = context.getBean(SpringTopicManager.class);

        return SpringActorBehavior.builder(Notification.class, context)
                .withState(ctx -> {
                    // Get or create the same topic
                    SpringTopicRef<Notification> topic = topicManager
                            .topic(Notification.class)
                            .withName("notifications")
                            .create();
                    topic.subscribe(ctx.getSelf());
                    return new SubscriberBehavior(ctx);
                })
                .onMessage(Notification.class, SubscriberBehavior::onNotification)
                .build();
    }

    private static class SubscriberBehavior {
        private final SpringBehaviorContext<Notification> ctx;

        SubscriberBehavior(SpringBehaviorContext<Notification> ctx) {
            this.ctx = ctx;
        }

        private Behavior<Notification> onNotification(Notification notification) {
            ctx.getLog().info("Received: {} - {}", notification.type, notification.message);
            return Behaviors.same();
        }
    }
}
```

## Best Practices

**Topic naming**: Use descriptive, unique names

```java
// ✅ Good
topicManager.topic(ChatMessage.class).withName("chat-room-" + roomId)

// ❌ Bad: Too generic
topicManager.topic(Message.class).withName("topic")
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

**Lifecycle management**: Use `ownedBy(ctx)` for actor-scoped topics

```java
// ✅ Good: Topic dies with actor
SpringTopicRef<Event> topic = topicManager
        .topic(Event.class)
        .withName("my-events")
        .ownedBy(ctx)
        .create();

// ❌ Bad: Topic persists forever (unless intended)
SpringTopicRef<Event> topic = topicManager
        .topic(Event.class)
        .withName("my-events")
        .create();
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

**SpringTopicManager not available:**

Ensure the service is scanned by Spring:

```java
@SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
public class MyApplication {
    // ...
}
```

## Further Reading

- [Chat Example](../examples/chat.md) - Real-world pub/sub implementation
- [Sharded Actors](sharded-actors.md) - Combining sharding with pub/sub
- [Apache Pekko Documentation](https://pekko.apache.org/docs/pekko/current/typed/distributed-pub-sub.html)
