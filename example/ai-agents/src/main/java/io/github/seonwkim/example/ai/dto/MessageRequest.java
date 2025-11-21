package io.github.seonwkim.example.ai.dto;

/**
 * Request DTO for sending a message to the support system.
 */
public class MessageRequest {
    private String message;

    public MessageRequest() {}

    public MessageRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
