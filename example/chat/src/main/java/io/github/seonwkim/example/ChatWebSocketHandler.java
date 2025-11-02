package io.github.seonwkim.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.UserActor.Connect;
import io.github.seonwkim.example.UserActor.JoinRoom;
import io.github.seonwkim.example.UserActor.LeaveRoom;
import io.github.seonwkim.example.UserActor.SendMessage;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive WebSocket handler for chat messages. Uses Spring WebFlux for non-blocking WebSocket handling.
 * This handler is fully compatible with BlockHound and the actor model.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

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
        log.debug("New WebSocket connection for user {}", userId);

        // Create a sink for sending messages to the client (non-blocking)
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

        // Create UserActor context with the message sink
        UserActor.UserActorContext userActorContext = new UserActor.UserActorContext(actorSystem, userId, sink);

        // Convert CompletionStage to Mono and wait for actor to be ready before processing messages
        return Mono.fromCompletionStage(actorSystem
                        .actor(UserActor.class)
                        .withContext(userActorContext)
                        .spawn())
                .flatMap(userActor -> {
                    // Actor is now ready - store it and send connect message
                    userActors.put(userId, userActor);
                    userActor.tell(new Connect());
                    log.info("UserActor started for user {}", userId);

                    // Now process messages - actor is guaranteed to be ready
                    Mono<Void> input = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .flatMap(payload -> handleMessage(userId, payload))
                            .then();

                    // Send outgoing messages to client
                    Mono<Void> output = session.send(sink.asFlux().map(session::textMessage));

                    // Run input and output concurrently, cleanup when done
                    return Mono.zip(input, output)
                            .doFinally(signalType -> {
                                cleanup(userId);
                            })
                            .then();
                });
    }

    /**
     * Handle incoming WebSocket message. Offloads blocking JSON parsing to bounded elastic scheduler.
     */
    private Mono<Void> handleMessage(String userId, String payload) {
        return Mono.fromCallable(() -> {
                    try {
                        // Blocking JSON parsing - safe because we're on boundedElastic scheduler
                        JsonNode json = objectMapper.readTree(payload);
                        JsonNode typeNode = json.get("type");

                        if (typeNode == null || typeNode.isNull()) {
                            log.warn("Message from user {} missing 'type' field", userId);
                            return null;
                        }

                        String type = typeNode.asText();
                        log.debug("Handling message type '{}' from user {}", type, userId);

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
                                log.warn("Unknown message type '{}' from user {}", type, userId);
                                break;
                        }
                        return null;
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error(
                                "Failed to parse JSON from user {}: {}",
                                userId,
                                payload.substring(0, Math.min(100, payload.length())),
                                e);
                        return null;
                    } catch (Exception e) {
                        log.error("Error handling message from user {}", userId, e);
                        return null;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic()) // Offload blocking JSON parsing
                .then();
    }

    private void handleJoinRoom(String userId, JsonNode payload) {
        JsonNode roomIdNode = payload.get("roomId");
        if (roomIdNode == null || roomIdNode.isNull()) {
            log.warn("Join room request from user {} missing roomId field", userId);
            return;
        }

        String roomId = roomIdNode.asText();
        if (roomId.isEmpty()) {
            log.warn("Join room request from user {} has empty roomId", userId);
            return;
        }

        final var userActor = getUserActor(userId);
        if (userActor != null) {
            userActor.tell(new JoinRoom(roomId));
        } else {
            log.warn("UserActor not found for userId {} in handleJoinRoom", userId);
        }
    }

    private void handleLeaveRoom(String userId) {
        final var userActor = getUserActor(userId);
        if (userActor != null) {
            userActor.tell(new LeaveRoom());
        } else {
            log.warn("UserActor not found for userId {} in handleLeaveRoom", userId);
        }
    }

    private void handleChatMessage(String userId, JsonNode payload) {
        final var userActor = getUserActor(userId);
        if (userActor == null) {
            log.warn("UserActor not found for userId {} in handleChatMessage", userId);
            return;
        }

        JsonNode messageNode = payload.get("message");
        if (messageNode == null || messageNode.isNull()) {
            log.warn("Chat message from user {} missing message field", userId);
            return;
        }

        String messageText = messageNode.asText();
        if (!messageText.isEmpty()) {
            userActor.tell(new SendMessage(messageText));
        }
    }

    private SpringActorRef<UserActor.Command> getUserActor(String userId) {
        if (userId == null) {
            return null;
        }
        return userActors.get(userId);
    }

    /**
     * Clean up resources when WebSocket connection closes.
     */
    private void cleanup(String userId) {
        final var userActor = getUserActor(userId);
        if (userActor != null) {
            log.info("Cleaning up UserActor for user {}", userId);
            try {
                userActor.stop();
                userActors.remove(userId);
            } catch (Exception e) {
                log.error("Error during cleanup for user {}", userId, e);
                // Still remove from map even on error
                userActors.remove(userId);
            }
        } else {
            log.warn("Cleanup called for unknown user {}", userId);
        }
    }
}
