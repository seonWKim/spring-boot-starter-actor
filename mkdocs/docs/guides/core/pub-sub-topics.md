# Pub/Sub Topics

A topic delivers each published message to every actor subscribed to it. The publisher doesn't know who
the subscribers are or how many there are. In cluster mode, a message published on one node reaches the
subscribers on every node.

Use a topic for one-to-many messages: chat rooms, live notifications, "this data changed" events that
several actors care about. Don't use it for:

- one-to-one messages: send to the actor directly ([Messaging](messaging.md));
- request and reply: use ask;
- messages that must not be lost: topics deliver at most once;
- spreading work so that only one worker handles each message: use a [router](routers.md).

Topics come with the core starter. The `SpringTopicManager` bean is created for you.

## Names and message types

A topic is identified by a `TopicName`: a message type plus a name. Publishers and subscribers that use
the same type and name share the topic, on every node. Two topics with the same name but different
types are different topics.

The type matters because **subscribers receive the messages as commands**. A topic's type must be the
subscribers' command type (or a supertype of it): an actor of `SpringActor<UserActor.Command>` can
subscribe to a topic of `UserActor.Command`, and every message published there must be a
`UserActor.Command`.

=== "Java"

    ```java
    // In UserActor: the events users receive are some of its commands
    public interface Command extends JsonSerializable {}
    public record ChatPosted(String roomId, String userId, String text) implements Command {}

    // The topic for one room
    TopicName<UserActor.Command> room = TopicName.of(UserActor.Command.class, "chat-room-" + roomId);
    ```

=== "Kotlin"

    ```kotlin
    // In UserActor: the events users receive are some of its commands
    sealed interface Command : JsonSerializable
    data class ChatPosted(val roomId: String, val userId: String, val text: String) : Command

    // The topic for one room
    val room: TopicName<UserActor.Command> = TopicName.of(UserActor.Command::class.java, "chat-room-$roomId")
    ```

In cluster mode, messages cross nodes, so the message type must implement `JsonSerializable` or
`CborSerializable`. In local mode they don't have to.

## Getting a topic

Inject `SpringTopicManager` and call `topic(name)`, then one of:

| Method | What it does |
|---|---|
| `getOrCreate()` | Returns the topic, creating it on this node if it doesn't exist yet. Safe to call any number of times. |
| `create()` | Creates the topic; throws `TopicAlreadyExistsException` if it already exists on this node |
| `withTimeout(duration)` | How long `create`/`getOrCreate` may wait (default 5 s). On timeout they throw `TopicCreationTimeoutException`. |

Both calls **block the calling thread** until the topic is ready (usually much less than a millisecond).
Call them once and keep the `SpringTopicRef`, for example in a service field or in an actor's state:

=== "Java"

    ```java
    @Service
    public class AnnouncementService {
        private final SpringTopicRef<UserActor.Command> announcements;

        public AnnouncementService(SpringTopicManager topics) {
            this.announcements = topics
                    .topic(TopicName.of(UserActor.Command.class, "announcements"))
                    .getOrCreate();
        }

        public void announce(String text) {
            announcements.publish(new UserActor.ChatPosted("all", "system", text));
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Service
    class AnnouncementService(topics: SpringTopicManager) {

        private val announcements: SpringTopicRef<UserActor.Command> = topics
            .topic(TopicName.of(UserActor.Command::class.java, "announcements"))
            .getOrCreate()

        fun announce(text: String) {
            announcements.publish(UserActor.ChatPosted("all", "system", text))
        }
    }
    ```

## Publishing and subscribing

| Method on `SpringTopicRef<T>` | What it does |
|---|---|
| `publish(message)` | Sends the message to every current subscriber. Returns at once. With no subscribers, the message is dropped. |
| `subscribe(ref)` | Delivers the topic's messages to `ref` until it unsubscribes or stops. Subscribing twice is the same as once. |
| `unsubscribe(ref)` | Stops delivery to `ref`. Unsubscribing an actor that isn't subscribed does nothing. |
| `name()` | The topic's `TopicName` |
| `unwrap(Class)` | Pekko's topic actor, for features the starter doesn't wrap |

`subscribe` and `unsubscribe` take any `SpringActorRef`: a handle from `getOrSpawn`, `ctx.getSelf()`, or a
ref received in a message. A subscriber that stops is removed from the topic for you.

Subscribing takes effect shortly after the call, not instantly. A message published right after
`subscribe` can miss the new subscriber. In cluster mode, it also takes a moment for other nodes to learn
about a new subscriber.

## Example: chat rooms

The [chat example](../../examples/chat.md) has one actor per room and one per connected user. The room
actor owns the room's topic: it subscribes users when they join and publishes what they say. Simplified
from its `ChatRoomActor`:

=== "Java"

    ```java
    @Component
    public class ChatRoomActor implements SpringActor<ChatRoomActor.Command> {

        private final SpringTopicManager topics;

        public ChatRoomActor(SpringTopicManager topics) {
            this.topics = topics;
        }

        public interface Command extends JsonSerializable {}

        public record JoinRoom(String userId, SpringActorRef<UserActor.Command> user) implements Command {}
        public record LeaveRoom(String userId, SpringActorRef<UserActor.Command> user) implements Command {}
        public record SendMessage(String userId, String text) implements Command {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            String roomId = actorContext.actorId();
            SpringTopicRef<UserActor.Command> roomTopic = topics
                    .topic(TopicName.of(UserActor.Command.class, "chat-room-" + roomId))
                    .getOrCreate();

            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(JoinRoom.class, (ctx, msg) -> {
                        roomTopic.subscribe(msg.user());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(LeaveRoom.class, (ctx, msg) -> {
                        roomTopic.unsubscribe(msg.user());
                        return SpringActorBehavior.same();
                    })
                    .onMessage(SendMessage.class, (ctx, msg) -> {
                        roomTopic.publish(new UserActor.ChatPosted(roomId, msg.userId(), msg.text()));
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @Component
    class ChatRoomActor(private val topics: SpringTopicManager) : SpringActor<ChatRoomActor.Command> {

        sealed interface Command : JsonSerializable

        data class JoinRoom(val userId: String, val user: SpringActorRef<UserActor.Command>) : Command
        data class LeaveRoom(val userId: String, val user: SpringActorRef<UserActor.Command>) : Command
        data class SendMessage(val userId: String, val text: String) : Command

        override fun create(actorContext: SpringActorContext): SpringActorBehavior<Command> {
            val roomId = actorContext.actorId()
            val roomTopic: SpringTopicRef<UserActor.Command> = topics
                .topic(TopicName.of(UserActor.Command::class.java, "chat-room-$roomId"))
                .getOrCreate()

            return actorBehavior<Command>(actorContext)
                .onMessage(JoinRoom::class.java) { _, msg ->
                    roomTopic.subscribe(msg.user)
                    SpringActorBehavior.same()
                }
                .onMessage(LeaveRoom::class.java) { _, msg ->
                    roomTopic.unsubscribe(msg.user)
                    SpringActorBehavior.same()
                }
                .onMessage(SendMessage::class.java) { _, msg ->
                    roomTopic.publish(UserActor.ChatPosted(roomId, msg.userId, msg.text))
                    SpringActorBehavior.same()
                }
                .build()
        }
    }
    ```

The example's real room actor is a [sharded actor](../cluster/sharded-actors.md), so each room lives on
one node of the cluster while its users can be on any node.

## Delivery guarantees

- **At most once.** A message reaches each subscriber once or not at all. Messages to a subscriber that
  is stopping, on a node that is unreachable, or that subscribed a moment too late are lost.
- **Order.** Messages from one publisher arrive at each subscriber in the order they were published.
- **No history.** A new subscriber gets only messages published after it joined. To show recent history,
  keep it in an actor or a database and send it when someone joins.

## Pitfalls

- **Topic type and subscriber type differ.** `subscribe` takes a `SpringActorRef<T>` of the topic's
  type, so the compiler stops a mismatch. Make published events part of the subscribers' command type.
- **Creating topics on every call.** `getOrCreate()` blocks the caller briefly. Keep the ref.
- **Using `create()` where `getOrCreate()` is meant.** `create()` throws if the topic already exists.
- **Expecting delivery.** For messages that must arrive, send them directly and confirm with ask, or
  store them first.

## Related

- [Messaging](messaging.md): direct messages and ask
- [Receptionist](receptionist.md): finding actors by service name
- [Routers](routers.md): one message to one of many workers
- [Chat example](../../examples/chat.md): topics with sharded rooms
- [Cluster formation](../cluster/cluster-formation.md): running topics across nodes
