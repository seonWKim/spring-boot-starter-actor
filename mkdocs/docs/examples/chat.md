# Chat Example

![Chat Demo](../chat.gif)
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
        public final ActorRef<UserActor.Command> userRef;

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

    @Override
    public Behavior<Command> create(EntityContext<Command> ctx) {
        return Behaviors.setup(
                context -> {
                    final String roomId = ctx.getEntityId();
                    return chatRoom(roomId, new HashMap<>());
                });
    }

    private Behavior<Command> chatRoom(
            String roomId, Map<String, ActorRef<UserActor.Command>> connectedUsers) {
        return Behaviors.receive(Command.class)
                .onMessage(JoinRoom.class, msg -> {
                    // Add the user to the connected users
                    connectedUsers.put(msg.userId, msg.userRef);

                    // Notify all users that a new user has joined
                    UserActor.JoinRoom joinRoomCmd = new UserActor.JoinRoom(msg.userId, roomId);
                    broadcastCommand(connectedUsers, joinRoomCmd);

                    return chatRoom(roomId, connectedUsers);
                })
                .onMessage(LeaveRoom.class, msg -> {
                    // Remove the user from connected users
                    ActorRef<UserActor.Command> userRef = connectedUsers.remove(msg.userId);

                    if (userRef != null) {
                        // Notify the user that they left the room
                        UserActor.LeaveRoom leaveRoomCmd = new UserActor.LeaveRoom(msg.userId, roomId);
                        userRef.tell(leaveRoomCmd);

                        // Notify all remaining users that a user has left
                        broadcastCommand(connectedUsers, leaveRoomCmd);
                    }

                    return chatRoom(roomId, connectedUsers);
                })
                .onMessage(SendMessage.class, msg -> {
                    // Create a message received command
                    UserActor.ReceiveMessage receiveMessageCmd = 
                            new UserActor.ReceiveMessage(msg.userId, msg.message, roomId);

                    // Broadcast the message to all connected users
                    broadcastCommand(connectedUsers, receiveMessageCmd);

                    return Behaviors.same();
                })
                .build();
    }

    private void broadcastCommand(Map<String, ActorRef<UserActor.Command>> connectedUsers, UserActor.Command command) {
        connectedUsers.values().forEach(userRef -> userRef.tell(command));
    }

    // Other ShardedActor implementation methods...
}
```

### UserActor

`UserActor` represents a connected user and handles sending messages to the user's WebSocket connection. It's implemented as a SpringActor that receives commands from ChatRoomActor:

```java
@Component
public class UserActor implements SpringActor {

    private final ObjectMapper objectMapper;

    @Autowired
    public UserActor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Base interface for all commands that can be sent to the user actor. */
    public interface Command extends JsonSerializable {}

    /** Command sent when a user joins a room. */
    public static class JoinRoom implements Command {
        public final String userId;
        public final String roomId;

        // Constructor and properties...
    }

    /** Command sent when a user leaves a room. */
    public static class LeaveRoom implements Command {
        public final String userId;
        public final String roomId;

        // Constructor and properties...
    }

    /** Command sent when a message is sent to a room. */
    public static class SendMessage implements Command {
        public final String userId;
        public final String message;
        public final String roomId;

        // Constructor and properties...
    }

    /** Command sent when a message is received from a room. */
    public static class ReceiveMessage implements Command {
        public final String userId;
        public final String message;
        public final String roomId;

        // Constructor and properties...
    }

    @Override
    public Class<?> commandClass() {
        return Command.class;
    }

    @Override
    public Behavior<Command> create(io.github.seonwkim.core.SpringActorContext actorContext) {
        if (!(actorContext instanceof UserActorContext)) {
            throw new IllegalArgumentException("Expected UserActorContext but got " + actorContext.getClass().getName());
        }

        UserActorContext userActorContext = (UserActorContext) actorContext;
        final String id = userActorContext.actorId();
        final WebSocketSession session = userActorContext.getSession();

        return Behaviors.setup(
                context -> {
                    context.getLog().info("Creating user actor with ID: {}", id);

                    if (session == null) {
                        context.getLog().error("Session not found for user ID: {}", id);
                        return Behaviors.empty();
                    }

                    return new UserActorBehavior(context, session, objectMapper).create();
                });
    }

    // Inner class to isolate stateful behavior logic
    private static class UserActorBehavior {
        private final ActorContext<Command> context;
        private final WebSocketSession session;
        private final ObjectMapper objectMapper;

        UserActorBehavior(
                ActorContext<Command> context,
                WebSocketSession session,
                ObjectMapper objectMapper) {
            this.context = context;
            this.session = session;
            this.objectMapper = objectMapper;
        }

        public Behavior<Command> create() {
            return Behaviors.receive(Command.class)
                    .onMessage(JoinRoom.class, this::onJoinRoom)
                    .onMessage(LeaveRoom.class, this::onLeaveRoom)
                    .onMessage(SendMessage.class, this::onSendMessage)
                    .onMessage(ReceiveMessage.class, this::onReceiveMessage)
                    .build();
        }

        private Behavior<Command> onJoinRoom(JoinRoom command) {
            sendEvent(
                    "user_joined",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onLeaveRoom(LeaveRoom command) {
            sendEvent(
                    "user_left",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
        }

        private Behavior<Command> onSendMessage(SendMessage command) {
            // This is a command from the user to send a message to the room
            // We don't need to send anything to the WebSocket here
            return Behaviors.same();
        }

        private Behavior<Command> onReceiveMessage(ReceiveMessage command) {
            sendEvent(
                    "message",
                    builder -> {
                        builder.put("userId", command.userId);
                        builder.put("message", command.message);
                        builder.put("roomId", command.roomId);
                    });
            return Behaviors.same();
        }

        private void sendEvent(String type, EventBuilder builder) {
            try {
                ObjectNode eventNode = objectMapper.createObjectNode();
                eventNode.put("type", type);
                builder.build(eventNode);

                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(eventNode)));
                }
            } catch (IOException e) {
                context.getLog().error("Failed to send message to WebSocket", e);
            }
        }
    }

    @FunctionalInterface
    private interface EventBuilder {
        void build(ObjectNode node);
    }
}
```

### UserActorContext

`UserActorContext` is a custom implementation of `SpringActorContext` that holds both the actor ID and the WebSocket session. This allows the UserActor to directly access the WebSocket session without using static fields:

```java
public class UserActorContext implements SpringActorContext {

    private final String id;
    private final WebSocketSession session;

    /**
     * Creates a new UserActorContext with the given ID and WebSocket session.
     *
     * @param id The ID of the actor
     * @param session The WebSocket session
     */
    public UserActorContext(String id, WebSocketSession session) {
        this.id = id;
        this.session = session;
    }

    @Override
    public String actorId() {
        return id;
    }

    /**
     * Gets the WebSocket session associated with this actor.
     *
     * @return The WebSocket session
     */
    public WebSocketSession getSession() {
        return session;
    }
}
```

### ChatService

`ChatService` manages the interaction between WebSocket connections and chat room actors:

```java
@Service
public class ChatService {
    private final SpringActorSystem actorSystem;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userRooms = new ConcurrentHashMap<>();

    public ChatService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Registers a WebSocket session for a user.
     *
     * @param userId The ID of the user
     * @param session The WebSocket session
     */
    public void registerSession(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    /**
     * Removes a WebSocket session for a user.
     *
     * @param userId The ID of the user
     */
    public void removeSession(String userId) {
        sessions.remove(userId);
        String roomId = userRooms.remove(userId);
        if (roomId != null) {
            leaveRoom(userId, roomId);
        }
    }

    /**
     * Joins a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     * @param userRef The actor reference for the user
     */
    public void joinRoom(String userId, String roomId, ActorRef<UserActor.Command> userRef) {
        SpringShardedActorRef<ChatRoomActor.Command> roomRef =
                actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

        roomRef.tell(new ChatRoomActor.JoinRoom(userId, userRef));
        userRooms.put(userId, roomId);
    }

    /**
     * Leaves a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     */
    public void leaveRoom(String userId, String roomId) {
        SpringShardedActorRef<ChatRoomActor.Command> roomRef =
                actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

        roomRef.tell(new ChatRoomActor.LeaveRoom(userId));
        userRooms.remove(userId);
    }

    /**
     * Sends a message to a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     * @param message The message to send
     */
    public void sendMessage(String userId, String roomId, String message) {
        SpringShardedActorRef<ChatRoomActor.Command> roomRef =
                actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId);

        roomRef.tell(new ChatRoomActor.SendMessage(userId, message));
    }

    /**
     * Gets the WebSocket session for a user.
     *
     * @param userId The ID of the user
     * @return The WebSocket session, or null if not found
     */
    public WebSocketSession getSession(String userId) {
        return sessions.get(userId);
    }

    /**
     * Gets the room ID for a user.
     *
     * @param userId The ID of the user
     * @return The room ID, or null if the user is not in a room
     */
    public String getUserRoom(String userId) {
        return userRooms.get(userId);
    }
}
```

### ChatWebSocketHandler

`ChatWebSocketHandler` handles WebSocket connections and messages:

```java
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final SpringActorSystem actorSystem;

    public ChatWebSocketHandler(
            ObjectMapper objectMapper, ChatService chatService, SpringActorSystem actorSystem) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.actorSystem = actorSystem;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Generate a unique user ID for this session
        String userId = UUID.randomUUID().toString();
        session.getAttributes().put("userId", userId);

        // Register the session with the chat service
        chatService.registerSession(userId, session);

        // Send a welcome message with the user ID
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("type", "connected");
            response.put("userId", userId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.get("type").asText();

        switch (type) {
            case "join":
                handleJoinRoom(session, userId, payload);
                break;
            case "leave":
                handleLeaveRoom(session, userId);
                break;
            case "message":
                handleChatMessage(session, userId, payload);
                break;
            default:
                sendErrorMessage(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            chatService.removeSession(userId);
        }
    }

    private void handleJoinRoom(WebSocketSession session, String userId, JsonNode payload) {
        String roomId = payload.get("roomId").asText();

        try {
            // Create a UserActorContext with the session
            UserActorContext userActorContext = new UserActorContext("user-" + userId, session);

            // Use SpringActorSystem's spawn method to create the actor with the context
            CompletionStage<SpringActorRef<UserActor.Command>> actorRefFuture =
                    actorSystem.spawn(UserActor.Command.class, userActorContext);

            actorRefFuture
                    .thenAccept(
                            actorRef -> {
                                // Join the room
                                chatService.joinRoom(userId, roomId, actorRef.getRef());

                                // Send confirmation
                                try {
                                    ObjectNode response = objectMapper.createObjectNode();
                                    response.put("type", "joined");
                                    response.put("roomId", roomId);
                                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    sendErrorMessage(session, "Failed to send join confirmation: " + e.getMessage());
                                }
                            })
                    .exceptionally(
                            ex -> {
                                ex.printStackTrace();
                                sendErrorMessage(session, "Failed to create actor: " + ex.getMessage());
                                return null;
                            });
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorMessage(session, "Failed to join room: " + e.getMessage());
        }
    }

    private void handleLeaveRoom(WebSocketSession session, String userId) {
        String roomId = chatService.getUserRoom(userId);
        if (roomId != null) {
            chatService.leaveRoom(userId, roomId);

            // Send confirmation
            try {
                ObjectNode response = objectMapper.createObjectNode();
                response.put("type", "left");
                response.put("roomId", roomId);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleChatMessage(WebSocketSession session, String userId, JsonNode payload) {
        String roomId = chatService.getUserRoom(userId);
        if (roomId != null) {
            String messageText = payload.get("message").asText();
            chatService.sendMessage(userId, roomId, messageText);
        } else {
            sendErrorMessage(session, "You are not in a room");
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            if (session.isOpen()) {
                ObjectNode response = objectMapper.createObjectNode();
                response.put("type", "error");
                response.put("message", errorMessage);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

## Architecture Benefits

This architecture eliminates the need for third-party middleware by leveraging:

- Built-in message routing between actors
- Natural state management within actors
- Scalability through sharded actors
- Real-time communication via WebSockets
- Fault tolerance provided by the actor system

## Key Takeaways

- The actor model provides an effective way to handle real-time communication
- WebSockets combined with actors create an efficient messaging system
- The resulting architecture is simpler and easier to maintain
