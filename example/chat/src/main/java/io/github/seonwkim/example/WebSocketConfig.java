package io.github.seonwkim.example;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration for WebSocket support. Registers the WebSocket handler and configures allowed
 * origins.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final ChatWebSocketHandler chatWebSocketHandler;

	public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
		this.chatWebSocketHandler = chatWebSocketHandler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry
				.addHandler(chatWebSocketHandler, "/ws/chat")
				.setAllowedOrigins("*"); // For development; restrict in production
	}
}
