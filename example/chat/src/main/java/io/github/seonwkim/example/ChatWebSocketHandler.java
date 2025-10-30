package io.github.seonwkim.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.UserActor.Connect;
import io.github.seonwkim.example.UserActor.JoinRoom;
import io.github.seonwkim.example.UserActor.LeaveRoom;
import io.github.seonwkim.example.UserActor.SendMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reactive WebSocket handler for chat messages. Uses Spring WebFlux for non-blocking WebSocket handling.
 * This handler is fully compatible with BlockHound and the actor model.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SpringActorSystem actorSystem;
    private final ConcurrentMap<String, SpringActorRef<UserActor.Command>> userActors = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper objectMapper, SpringActorSystem actorSystem) {
        this.objectMapper = objectMapper;
        this.actorSystem = actorSystem;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String userId = UUID.randomUUID().toString();

        // Create a sink for sending messages to the client (non-blocking)
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Create UserActor context with the message sink
        UserActor.UserActorContext userActorContext =
                new UserActor.UserActorContext(actorSystem, objectMapper, userId, sink);

        // Start the actor
        actorSystem.actor(UserActor.class)
                .withContext(userActorContext)
                .start()
                .thenAccept(userActor -> {
                    userActors.put(userId, userActor);
                    userActor.tell(new Connect());
                });

        // Handle incoming messages from client
        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(payload -> handleMessage(userId, payload))
                .then();

        // Send outgoing messages to client
        Mono<Void> output = session.send(
                sink.asFlux()
                        .map(session::textMessage)
        );

        // Run input and output concurrently, cleanup when done
        return Mono.zip(input, output)
                .doFinally(signalType -> cleanup(userId))
                .then();
    }

    private Mono<Void> handleMessage(String userId, String payload) {
        return Mono.fromRunnable(() -> {
            try {
                JsonNode json = objectMapper.readTree(payload);
                String type = json.get("type").asText();

                switch (type) {
                    case "join":
                        handleJoinRoom(userId, json);
                        break;
                    case "leave":
                        handleLeaveRoom(userId);
                        break;
                    case "message":
                        handleChatMessage(userId, json);
                        break;
                    default:
                        // Unknown message type - log or ignore
                        break;
                }
            } catch (Exception e) {
                // Log error but don't break the stream
                e.printStackTrace();
            }
        });
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

    private void cleanup(String userId) {
        final var userActor = getUserActor(userId);
        if (userActor != null) {
            userActor.stop();
            userActors.remove(userId);
        }
    }
}
