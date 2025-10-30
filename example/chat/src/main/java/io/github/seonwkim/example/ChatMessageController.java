package io.github.seonwkim.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for sending messages to chat rooms.
 * Provides an HTTP API for external clients to send messages.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatMessageController {

    private final ChatMessageSenderService chatMessageSenderService;

    public ChatMessageController(ChatMessageSenderService chatMessageSenderService) {
        this.chatMessageSenderService = chatMessageSenderService;
    }

    /**
     * Endpoint to send a message to a specific chat room.
     *
     * @param request The message request containing roomId, userId, and message
     * @return ResponseEntity indicating success or failure
     */
    @PostMapping("/send")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        if (request.getRoomId() == null || request.getRoomId().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new SendMessageResponse(false, "Room ID is required"));
        }

        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new SendMessageResponse(false, "User ID is required"));
        }

        if (request.getMessage() == null || request.getMessage().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new SendMessageResponse(false, "Message is required"));
        }

        try {
            chatMessageSenderService.sendMessage(
                    request.getRoomId(),
                    request.getUserId(),
                    request.getMessage());

            return ResponseEntity.ok(new SendMessageResponse(true, "Message sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new SendMessageResponse(false, "Failed to send message: " + e.getMessage()));
        }
    }

    /**
     * Request object for sending a message.
     */
    public static class SendMessageRequest {
        private String roomId;
        private String userId;
        private String message;

        public SendMessageRequest() {}

        public SendMessageRequest(String roomId, String userId, String message) {
            this.roomId = roomId;
            this.userId = userId;
            this.message = message;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * Response object for send message operation.
     */
    public static class SendMessageResponse {
        private boolean success;
        private String message;

        public SendMessageResponse() {}

        public SendMessageResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
