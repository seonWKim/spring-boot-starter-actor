// WebSocket message types
export interface WebSocketMessage {
  type: string;
  [key: string]: any;
}

export interface ConnectedMessage extends WebSocketMessage {
  type: 'connected';
  userId: string;
}

export interface JoinedMessage extends WebSocketMessage {
  type: 'joined';
  roomId: string;
}

export interface LeftMessage extends WebSocketMessage {
  type: 'left';
  roomId: string;
}

export interface UserJoinedMessage extends WebSocketMessage {
  type: 'user_joined';
  userId: string;
  roomId: string;
}

export interface UserLeftMessage extends WebSocketMessage {
  type: 'user_left';
  userId: string;
  roomId: string;
}

export interface ChatMessage extends WebSocketMessage {
  type: 'message';
  userId: string;
  message: string;
  roomId: string;
}

export interface ErrorMessage extends WebSocketMessage {
  type: 'error';
  message: string;
}

export type MessageHandler = (message: WebSocketMessage, nodeId?: string) => void;

export class WebSocketService {
  private socket: WebSocket | null = null;
  private messageHandlers: Set<MessageHandler> = new Set();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private isIntentionallyClosed = false;

  connect(url?: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = url || `${protocol}//${window.location.host}/ws/chat`;

      this.isIntentionallyClosed = false;
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        console.log('WebSocket connected');
        this.reconnectAttempts = 0;
        resolve();
      };

      this.socket.onclose = () => {
        console.log('WebSocket disconnected');
        if (!this.isIntentionallyClosed && this.reconnectAttempts < this.maxReconnectAttempts) {
          setTimeout(() => {
            this.reconnectAttempts++;
            console.log(`Reconnecting... (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            this.connect(wsUrl);
          }, this.reconnectDelay);
        }
      };

      this.socket.onerror = (error) => {
        console.error('WebSocket error:', error);
        reject(error);
      };

      this.socket.onmessage = (event) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);
          this.messageHandlers.forEach(handler => handler(message));
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error);
        }
      };
    });
  }

  disconnect() {
    this.isIntentionallyClosed = true;
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  send(message: WebSocketMessage) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message));
    } else {
      console.error('WebSocket is not connected');
    }
  }

  addMessageHandler(handler: MessageHandler) {
    this.messageHandlers.add(handler);
  }

  removeMessageHandler(handler: MessageHandler) {
    this.messageHandlers.delete(handler);
  }

  isConnected(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  // Helper methods for sending specific message types
  joinRoom(roomId: string) {
    this.send({ type: 'join', roomId });
  }

  leaveRoom() {
    this.send({ type: 'leave' });
  }

  sendMessage(message: string) {
    this.send({ type: 'message', message });
  }
}

// Singleton instance
export const websocketService = new WebSocketService();
