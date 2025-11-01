package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for sending messages to chat rooms.
 * This service provides a simple API for sending messages to specific chat rooms.
 */
@Service
public class ChatMessageSenderService {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageSenderService.class);

    private final SpringActorSystem springActorSystem;

    public ChatMessageSenderService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Sends a message to a specific chat room.
     *
     * @param roomId The ID of the chat room
     * @param userId The ID of the user sending the message
     * @param message The message content
     */
    public void sendMessage(String roomId, String userId, String message) {
        try {
            SpringShardedActorRef<ChatRoomActor.Command> roomRef = springActorSystem
                    .sharded(ChatRoomActor.class)
                    .withId(roomId)
                    .get();

            ChatRoomActor.SendMessage sendMessageCmd = new ChatRoomActor.SendMessage(userId, message);
            roomRef.tell(sendMessageCmd);

            logger.debug("Sent message to room {} from user {}: {}", roomId, userId, message);
        } catch (Exception e) {
            logger.error("Failed to send message to room {} from user {}", roomId, userId, e);
        }
    }
}
