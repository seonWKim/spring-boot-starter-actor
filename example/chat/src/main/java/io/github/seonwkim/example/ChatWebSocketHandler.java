package io.github.seonwkim.example;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSpawnContext;
import io.github.seonwkim.core.SpringActorStopContext;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.UserActor.Connect;
import io.github.seonwkim.example.UserActor.JoinRoom;
import io.github.seonwkim.example.UserActor.LeaveRoom;
import io.github.seonwkim.example.UserActor.SendMessage;

/**
 * WebSocket handler for chat messages. Handles WebSocket connections and messages, and connects
 * them to the actor system.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SpringActorSystem actorSystem;
    private final ConcurrentMap<String, SpringActorRef<UserActor.Command>> userActors =
            new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ObjectMapper objectMapper, SpringActorSystem actorSystem) {
        this.objectMapper = objectMapper;
        this.actorSystem = actorSystem;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = UUID.randomUUID().toString();
        session.getAttributes().put("userId", userId);
        UserActor.UserActorContext userActorContext =
                new UserActor.UserActorContext(actorSystem, objectMapper, userId, session);

        final SpringActorSpawnContext<UserActor, UserActor.Command> spawnContext =
                new SpringActorSpawnContext.Builder<UserActor, UserActor.Command>()
                        .actorClass(UserActor.class)
                        .actorContext(userActorContext)
                        .build();
        actorSystem.spawn(spawnContext)
                   .thenAccept(userActor -> {
                       userActors.put(userId, userActor);
                       userActor.tell(new Connect());
                   });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.get("type").asText();

        switch (type) {
            case "join":
                handleJoinRoom(userId, payload);
                break;
            case "leave":
                handleLeaveRoom(userId);
                break;
            case "message":
                handleChatMessage(userId, payload);
                break;
            default:
                sendErrorMessage(session, "Unknown message type: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        final String userId = (String) session.getAttributes().get("userId");
        final var userActor = getUserActor(userId);
        if (userId != null && userActor != null) {
            final SpringActorStopContext<UserActor, UserActor.Command> stopContext =
                    new SpringActorStopContext.Builder<UserActor, UserActor.Command>()
                            .actorClass(UserActor.class)
                            .actorId(userId)
                            .build();
            actorSystem.stop(stopContext);
            userActors.remove(userId);
        }
    }

    private void handleJoinRoom(String userId, JsonNode payload) {
        String roomId = payload.get("roomId").asText();
        final var userActor = getUserActor(userId);
        if (roomId != null && userActor != null) {
            userActor.tell(new JoinRoom(roomId));
        }
    }

    private void handleLeaveRoom(String userId) {
        final var userActor = getUserActor(userId);
        if (userActor != null) {
            userActor.tell(new LeaveRoom());
        }
    }

    private void handleChatMessage(String userId, JsonNode payload) {
        final var userActor = getUserActor(userId);
        String messageText = payload.get("message").asText();
        if (userActor != null && messageText != null) {
            userActor.tell(new SendMessage(messageText));
        }
    }

    private SpringActorRef<UserActor.Command> getUserActor(String userId) {
        if (userId == null) {
            return null;
        }

        return userActors.get(userId);
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
