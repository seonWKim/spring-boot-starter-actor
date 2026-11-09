package io.github.seonwkim.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.streams.ActorStreams;
import io.github.seonwkim.actor.streams.websocket.ActorWebSocketHandler;
import io.github.seonwkim.example.UserActor.Connect;
import io.github.seonwkim.example.UserActor.JoinRoom;
import io.github.seonwkim.example.UserActor.LeaveRoom;
import io.github.seonwkim.example.UserActor.SendMessage;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * The chat's WebSocket endpoint: one {@link UserActor} per connection, through the starter's
 * {@link ActorWebSocketHandler}. The user actor sends JSON events to the connection's client reference;
 * the client's JSON frames become {@link UserActor} commands. When the client disconnects, the client
 * reference stops and the user actor, which watches it, stops too.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final ActorWebSocketHandler bridge;

    public ChatWebSocketHandler(ObjectMapper objectMapper, SpringActorSystem actorSystem, ActorStreams streams) {
        this.objectMapper = objectMapper;
        this.bridge = new ActorWebSocketHandler(streams, (session, client) -> actorSystem
                .actor(UserActor.class)
                .withContext(new UserActor.UserActorContext(
                        actorSystem, UUID.randomUUID().toString(), client))
                .spawn()
                .thenApply(this::connect));
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return bridge.handle(session);
    }

    private Consumer<String> connect(SpringActorHandle<UserActor.Command> user) {
        user.tell(new Connect());
        return payload -> {
            UserActor.Command command = parse(payload);
            if (command != null) {
                user.tell(command);
            }
        };
    }

    /** The command for a client frame, or null when the frame is not one the chat understands. */
    @Nullable private UserActor.Command parse(String payload) {
        JsonNode json;
        try {
            json = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.warn("Ignoring a frame that is not JSON: {}", payload.substring(0, Math.min(100, payload.length())));
            return null;
        }
        String type = json.path("type").asText("");
        switch (type) {
            case "join":
                String roomId = json.path("roomId").asText("");
                return roomId.isEmpty() ? null : new JoinRoom(roomId);
            case "leave":
                return new LeaveRoom();
            case "message":
                String message = json.path("message").asText("");
                return message.isEmpty() ? null : new SendMessage(message);
            default:
                log.warn("Ignoring a frame of unknown type '{}'", type);
                return null;
        }
    }
}
