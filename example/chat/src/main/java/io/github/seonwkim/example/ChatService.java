package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.pekko.actor.typed.ActorRef;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

/**
 * Service that handles interactions with chat rooms. It serves as an intermediary between WebSocket
 * sessions and the actor system.
 */
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
	public void joinRoom(String userId, String roomId, ActorRef<ChatRoomActor.ChatEvent> userRef) {
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
