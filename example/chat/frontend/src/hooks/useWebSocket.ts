import { useEffect, useState, useCallback } from 'react';
import { websocketService, type MessageHandler } from '../services/websocket';

export function useWebSocket() {
  const [isConnected, setIsConnected] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);
  const [currentRoomId, setCurrentRoomId] = useState<string | null>(null);

  useEffect(() => {
    websocketService.connect()
      .then(() => setIsConnected(true))
      .catch(console.error);

    return () => {
      websocketService.disconnect();
    };
  }, []);

  const addMessageHandler = useCallback((handler: MessageHandler) => {
    websocketService.addMessageHandler(handler);
    return () => websocketService.removeMessageHandler(handler);
  }, []);

  const joinRoom = useCallback((roomId: string) => {
    websocketService.joinRoom(roomId);
    setCurrentRoomId(roomId);
  }, []);

  const leaveRoom = useCallback(() => {
    websocketService.leaveRoom();
    setCurrentRoomId(null);
  }, []);

  const sendMessage = useCallback((message: string) => {
    websocketService.sendMessage(message);
  }, []);

  return {
    isConnected,
    userId,
    setUserId,
    currentRoomId,
    joinRoom,
    leaveRoom,
    sendMessage,
    addMessageHandler,
  };
}
