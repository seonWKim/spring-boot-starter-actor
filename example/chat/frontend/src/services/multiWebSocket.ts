import type { WebSocketMessage, MessageHandler } from './websocket';

export class MultiWebSocketService {
  private socket: WebSocket | null = null;
  private messageHandlers: Set<MessageHandler> = new Set();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private isIntentionallyClosed = false;
  private currentPort: number;
  private nodeId: string;

  constructor(port: number, nodeId: string) {
    this.currentPort = port;
    this.nodeId = nodeId;
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = window.location.hostname;
      const wsUrl = `${protocol}//${host}:${this.currentPort}/ws/chat`;

      this.isIntentionallyClosed = false;
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        console.log(`[${this.nodeId}] WebSocket connected to port ${this.currentPort}`);
        this.reconnectAttempts = 0;
        resolve();
      };

      this.socket.onclose = () => {
        console.log(`[${this.nodeId}] WebSocket disconnected from port ${this.currentPort}`);
        if (!this.isIntentionallyClosed && this.reconnectAttempts < this.maxReconnectAttempts) {
          setTimeout(() => {
            this.reconnectAttempts++;
            console.log(`[${this.nodeId}] Reconnecting... (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            this.connect();
          }, this.reconnectDelay);
        }
      };

      this.socket.onerror = (error) => {
        console.error(`[${this.nodeId}] WebSocket error:`, error);
        reject(error);
      };

      this.socket.onmessage = (event) => {
        try {
          const message: WebSocketMessage = JSON.parse(event.data);
          this.messageHandlers.forEach(handler => handler(message, this.nodeId));
        } catch (error) {
          console.error(`[${this.nodeId}] Failed to parse WebSocket message:`, error);
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
      console.error(`[${this.nodeId}] WebSocket is not connected`);
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

  joinRoom(roomId: string) {
    this.send({ type: 'join', roomId });
  }

  leaveRoom() {
    this.send({ type: 'leave' });
  }

  sendMessage(message: string) {
    this.send({ type: 'message', message });
  }

  getNodeId(): string {
    return this.nodeId;
  }

  getPort(): number {
    return this.currentPort;
  }
}
