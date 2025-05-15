# Chat Example

This guide demonstrates how to build a real-time chat application using Spring Boot Starter Actor without introducing third-party middleware.

## Overview

The chat example shows how to:

- Build a real-time chat application using actors
- Implement WebSocket communication for real-time messaging
- Create a scalable, clustered chat system
- Eliminate the need for external message brokers or middleware

This example demonstrates how Spring Boot Starter Actor can be used to build real-world applications efficiently without relying on additional infrastructure components.

## Source Code

You can find the complete source code for this example on GitHub:
[https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/chat](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/chat)

## Key Components

### ChatRoomActor

`ChatRoomActor` is a sharded actor that manages a chat room. Each chat room is a separate entity identified by a room ID. The actor maintains a list of connected users and broadcasts messages to all users in the room:

```java
@Component
public class ChatRoomActor implements ShardedActor<ChatRoomActor.Command> {
    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "ChatRoomActor");

    // Command interface and message types
    public interface Command extends JsonSerializable {}

    public static class JoinRoom implements Command {
        public final String userId;
        public final ActorRef<ChatEvent> userRef;

        // Constructor and properties...
    }

    public static class LeaveRoom implements Command {
        public final String userId;

        // Constructor and properties...
    }

    public static class SendMessage implements Command {
        public final String userId;
        public final String message;

        // Constructor and properties...
    }

    // Event interface and event types
    public interface ChatEvent extends JsonSerializable {}

    public static class UserJoined implements ChatEvent {
        public final String userId;
        public final String roomId;

        // Constructor and properties...
    }

    public static class UserLeft implements ChatEvent {
        public final String userId;
        public final String roomId;

        // Constructor and properties...
    }

    public static class MessageReceived implements ChatEvent {
        public final String userId;
        public final String message;
        public final String roomId;

        // Constructor and properties...
    }

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(
                context -> {
                    final String roomId = ctx.getEntityId();
                    return chatRoom(roomId, new HashMap<>());
                });
    }

    private Behavior<Command> chatRoom(
            String roomId, Map<String, ActorRef<ChatEvent>> connectedUsers) {
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

    private void broadcastEvent(Map<String, ActorRef<ChatEvent>> connectedUsers, ChatEvent event) {
        connectedUsers.values().forEach(userRef -> userRef.tell(event));
    }

    // Other ShardedActor implementation methods...
}
```

### UserActor

`UserActor` represents a connected user and handles sending messages to the user's WebSocket connection:

```java
public class UserActor {
    private final String userId;
    private final WebSocketSession session;
    private final ObjectMapper objectMapper;

    public UserActor(String userId, WebSocketSession session, ObjectMapper objectMapper) {
        this.userId = userId;
        this.session = session;
        this.objectMapper = objectMapper;
    }

    public Behavior<ChatRoomActor.ChatEvent> create() {
        return Behaviors.receive(ChatRoomActor.ChatEvent.class)
                .onMessage(ChatRoomActor.ChatEvent.class, this::onChatEvent)
                .build();
    }

    private Behavior<ChatRoomActor.ChatEvent> onChatEvent(ChatRoomActor.ChatEvent event) {
        try {
            // Convert the event to JSON and send it to the WebSocket
            String json = objectMapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            // Handle error
        }
        return Behaviors.same();
    }
}
```

### ChatService

`ChatService` manages the interaction between WebSocket connections and chat room actors:

```java
@Service
public class ChatService {
    private final SpringActorSystem springActorSystem;
    private final ObjectMapper objectMapper;
    private final Map<String, ActorRef<ChatRoomActor.ChatEvent>> userActors = new ConcurrentHashMap<>();

    public ChatService(SpringActorSystem springActorSystem, ObjectMapper objectMapper) {
        this.springActorSystem = springActorSystem;
        this.objectMapper = objectMapper;
    }

    public void handleUserConnection(String userId, WebSocketSession session) {
        // Create a user actor for this connection
        ActorRef<ChatRoomActor.ChatEvent> userActor = springActorSystem.spawn(
                new UserActor(userId, session, objectMapper).create(),
                userId);

        // Store the user actor reference
        userActors.put(userId, userActor);
    }

    public void handleUserDisconnection(String userId) {
        // Remove the user actor reference
        userActors.remove(userId);
    }

    public void joinRoom(String userId, String roomId) {
        // Get the user actor reference
        ActorRef<ChatRoomActor.ChatEvent> userActor = userActors.get(userId);

        if (userActor != null) {
            // Get a reference to the chat room actor
            SpringShardedActorRef<ChatRoomActor.Command> roomActor =
                    springActorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

            // Send a join room message to the chat room actor
            roomActor.tell(new ChatRoomActor.JoinRoom(userId, userActor));
        }
    }

    public void leaveRoom(String userId, String roomId) {
        // Get a reference to the chat room actor
        SpringShardedActorRef<ChatRoomActor.Command> roomActor =
                springActorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

        // Send a leave room message to the chat room actor
        roomActor.tell(new ChatRoomActor.LeaveRoom(userId));
    }

    public void sendMessage(String userId, String roomId, String message) {
        // Get a reference to the chat room actor
        SpringShardedActorRef<ChatRoomActor.Command> roomActor =
                springActorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

        // Send a message to the chat room actor
        roomActor.tell(new ChatRoomActor.SendMessage(userId, message));
    }
}
```

### ChatWebSocketHandler

`ChatWebSocketHandler` handles WebSocket connections and messages:

```java
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Extract user ID from session attributes
        String userId = (String) session.getAttributes().get("userId");

        // Register the user with the chat service
        chatService.handleUserConnection(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Extract user ID from session attributes
        String userId = (String) session.getAttributes().get("userId");

        // Unregister the user from the chat service
        chatService.handleUserDisconnection(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Extract user ID from session attributes
        String userId = (String) session.getAttributes().get("userId");

        // Parse the message
        JsonNode jsonNode = objectMapper.readTree(message.getPayload());
        String type = jsonNode.get("type").asText();

        switch (type) {
            case "join":
                String roomId = jsonNode.get("roomId").asText();
                chatService.joinRoom(userId, roomId);
                break;
            case "leave":
                roomId = jsonNode.get("roomId").asText();
                chatService.leaveRoom(userId, roomId);
                break;
            case "message":
                roomId = jsonNode.get("roomId").asText();
                String messageText = jsonNode.get("message").asText();
                chatService.sendMessage(userId, roomId, messageText);
                break;
        }
    }
}
```

## Why No Third-Party Middleware Is Needed

### 1. Built-in Message Routing

Spring Boot Starter Actor provides:

- Built-in message routing between actors
- Automatic distribution of actors across the cluster
- Location transparency for actor references

This eliminates the need for a separate message broker like RabbitMQ or Kafka.

### 2. State Management

Actors naturally manage state:

- Each chat room actor maintains its own list of connected users
- State is encapsulated within actors and not exposed directly
- State changes are driven by messages, ensuring consistency

This eliminates the need for a separate state store like Redis.

### 3. Scalability

The actor model provides scalability:

- Chat rooms are distributed across the cluster as sharded actors
- New nodes can be added to the cluster to handle more chat rooms
- The system scales horizontally without changes to the application code

This eliminates the need for complex scaling infrastructure.

### 4. Real-Time Communication

The combination of WebSockets and actors provides real-time communication:

- WebSockets provide a direct connection between clients and the server
- Actors handle message distribution efficiently
- Messages are delivered immediately without polling or delays

This eliminates the need for specialized real-time messaging services.

### 5. Fault Tolerance

The actor system provides fault tolerance:

- If an actor fails, it can be automatically restarted
- If a node fails, its actors are redistributed to other nodes
- The system continues to function even during partial failures

This eliminates the need for additional reliability layers.

## Benefits of This Approach

1. **Simplified Architecture**: Fewer components means less complexity and fewer points of failure
2. **Reduced Operational Overhead**: No need to manage and monitor additional services
3. **Lower Latency**: Direct communication without intermediate hops
4. **Cost Efficiency**: No licensing or operational costs for additional middleware
5. **Easier Deployment**: Simpler deployment with fewer dependencies

## Key Takeaways

- Spring Boot Starter Actor enables building real-world applications without third-party middleware
- The actor model provides a natural way to handle real-time communication and state management
- WebSockets combined with actors create an efficient real-time messaging system
- The resulting architecture is simpler, more cost-effective, and easier to maintain
- Complex applications like chat systems can be built with minimal infrastructure requirements
