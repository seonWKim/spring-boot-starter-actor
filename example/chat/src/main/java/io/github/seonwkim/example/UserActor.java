package io.github.seonwkim.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorContext;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Spring;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Actor that represents a user in the chat system. It receives events from chat rooms and forwards
 * them to the user's WebSocket session.
 */
@Component
public class UserActor implements SpringActor {
	// Store sessions and object mapper for each user ID
	private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private static ObjectMapper objectMapper;

	@Override
	public Class<?> commandClass() {
		return ChatRoomActor.ChatEvent.class;
	}

	/**
	 * Sets the object mapper to be used by all user actors.
	 *
	 * @param mapper The object mapper for JSON serialization
	 */
	public static void setObjectMapper(ObjectMapper mapper) {
		objectMapper = mapper;
	}

	/**
	 * Registers a session for a user ID.
	 *
	 * @param userId The ID of the user
	 * @param session The WebSocket session
	 */
	public static void registerSession(String userId, WebSocketSession session) {
		sessions.put(userId, session);
	}

	/**
	 * Creates a behavior for a user actor. This method is called by the actor system when a new user
	 * actor is created.
	 *
	 * @param actorContext The context of the actor
	 * @return A behavior for the actor
	 */
	@Override
	public Behavior<ChatRoomActor.ChatEvent> create(SpringActorContext actorContext) {
		final String id = actorContext.actorId();
		return Behaviors.setup(
				context -> {
					context.getLog().info("Creating user actor with ID: {}", id);
					// Extract the user ID from the actor ID (removing "user-" prefix)
					String userId = id.startsWith("user-") ? id.substring(5) : id;
					WebSocketSession session = sessions.get(userId);

					if (session == null || objectMapper == null) {
						context.getLog().error("Session or ObjectMapper not found for user ID: {}", userId);
						return Behaviors.empty();
					}

					return new UserActorBehavior(context, session, objectMapper).create();
				});
	}

	// Inner class to isolate stateful behavior logic
	private static class UserActorBehavior {
		private final ActorContext<ChatRoomActor.ChatEvent> context;
		private final WebSocketSession session;
		private final ObjectMapper objectMapper;

		UserActorBehavior(
				ActorContext<ChatRoomActor.ChatEvent> context,
				WebSocketSession session,
				ObjectMapper objectMapper) {
			this.context = context;
			this.session = session;
			this.objectMapper = objectMapper;
		}

		public Behavior<ChatRoomActor.ChatEvent> create() {
			return Behaviors.receive(ChatRoomActor.ChatEvent.class)
					.onMessage(ChatRoomActor.UserJoined.class, this::onUserJoined)
					.onMessage(ChatRoomActor.UserLeft.class, this::onUserLeft)
					.onMessage(ChatRoomActor.MessageReceived.class, this::onMessageReceived)
					.build();
		}

		private Behavior<ChatRoomActor.ChatEvent> onUserJoined(ChatRoomActor.UserJoined event) {
			sendEvent(
					"user_joined",
					builder -> {
						builder.put("userId", event.userId);
						builder.put("roomId", event.roomId);
					});
			return Behaviors.same();
		}

		private Behavior<ChatRoomActor.ChatEvent> onUserLeft(ChatRoomActor.UserLeft event) {
			sendEvent(
					"user_left",
					builder -> {
						builder.put("userId", event.userId);
						builder.put("roomId", event.roomId);
					});
			return Behaviors.same();
		}

		private Behavior<ChatRoomActor.ChatEvent> onMessageReceived(
				ChatRoomActor.MessageReceived event) {
			sendEvent(
					"message",
					builder -> {
						builder.put("userId", event.userId);
						builder.put("message", event.message);
						builder.put("roomId", event.roomId);
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
