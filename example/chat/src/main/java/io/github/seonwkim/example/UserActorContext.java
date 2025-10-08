package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorContext;
import org.springframework.web.socket.WebSocketSession;

/**
 * Custom SpringActorContext for UserActor that includes both actorId and WebSocketSession. This
 * allows the UserActor to directly access the WebSocketSession without using static fields.
 */
public class UserActorContext implements SpringActorContext {

    private final String id;
    private final WebSocketSession session;

    /**
     * Creates a new UserActorContext with the given ID and WebSocket session.
     *
     * @param id The ID of the actor
     * @param session The WebSocket session
     */
    public UserActorContext(String id, WebSocketSession session) {
        this.id = id;
        this.session = session;
    }

    @Override
    public String actorId() {
        return id;
    }

    /**
     * Gets the WebSocket session associated with this actor.
     *
     * @return The WebSocket session
     */
    public WebSocketSession getSession() {
        return session;
    }
}
