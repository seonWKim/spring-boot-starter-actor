package io.github.seonwkim.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for chat messages. Handles WebSocket connections and messages, and connects
 * them to the actor system.
 */
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
