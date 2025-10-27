import { useState, useCallback } from 'react'
import { ServerSelector } from './components/ServerSelector'
import { ChatPanel } from './components/ChatPanel'
import { Button } from './components/ui/button'
import type { MessageData } from './components/Message'
import type { WebSocketMessage, ConnectedMessage, ChatMessage, UserJoinedMessage, UserLeftMessage } from './services/websocket'
import { MultiWebSocketService } from './services/multiWebSocket'
import { LogOut } from 'lucide-react'

interface ServerConfig {
  id: string;
  port: number;
  label: string;
}

interface NodeState {
  service: MultiWebSocketService;
  messages: MessageData[];
  userId: string | null;
  isConnected: boolean;
  onlineUsers: Set<string>;
}

function App() {
  const [servers, setServers] = useState<ServerConfig[]>([]);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [nodeStates, setNodeStates] = useState<Map<string, NodeState>>(new Map());

  const handleConnect = useCallback((selectedServers: ServerConfig[], selectedRoomId: string) => {
    setServers(selectedServers);
    setRoomId(selectedRoomId);

    const newNodeStates = new Map<string, NodeState>();

    selectedServers.forEach(server => {
      const service = new MultiWebSocketService(server.port, server.id);

      const state: NodeState = {
        service,
        messages: [],
        userId: null,
        isConnected: false,
        onlineUsers: new Set(),
      };

      // Set up message handler
      service.addMessageHandler((message: WebSocketMessage, nodeId?: string) => {
        if (!nodeId) return;
        setNodeStates(prev => {
          const current = prev.get(nodeId);
          if (!current) return prev;

          const updated = new Map(prev);
          const newState = { ...current };

          switch (message.type) {
            case 'connected': {
              const connectedMsg = message as ConnectedMessage;
              newState.userId = connectedMsg.userId;
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `Connected with user ID: ${connectedMsg.userId.substring(0, 8)}...`,
                  timestamp: new Date(),
                }
              ];
              break;
            }

            case 'joined': {
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `You joined room: ${message.roomId}`,
                  timestamp: new Date(),
                }
              ];
              newState.onlineUsers = new Set([newState.userId!]);
              break;
            }

            case 'left': {
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `You left room: ${message.roomId}`,
                  timestamp: new Date(),
                }
              ];
              newState.onlineUsers = new Set();
              break;
            }

            case 'user_joined': {
              const userJoinedMsg = message as UserJoinedMessage;
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `User ${userJoinedMsg.userId.substring(0, 8)}... joined the room`,
                  timestamp: new Date(),
                }
              ];
              const newOnlineUsers = new Set(newState.onlineUsers);
              newOnlineUsers.add(userJoinedMsg.userId);
              newState.onlineUsers = newOnlineUsers;
              break;
            }

            case 'user_left': {
              const userLeftMsg = message as UserLeftMessage;
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `User ${userLeftMsg.userId.substring(0, 8)}... left the room`,
                  timestamp: new Date(),
                }
              ];
              const newOnlineUsers = new Set(newState.onlineUsers);
              newOnlineUsers.delete(userLeftMsg.userId);
              newState.onlineUsers = newOnlineUsers;
              break;
            }

            case 'message': {
              const chatMsg = message as ChatMessage;
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}-${Math.random()}`,
                  type: chatMsg.userId === newState.userId ? 'user' : 'other',
                  userId: chatMsg.userId,
                  content: chatMsg.message,
                  timestamp: new Date(),
                }
              ];
              break;
            }

            case 'error': {
              newState.messages = [
                ...newState.messages,
                {
                  id: `${Date.now()}-${nodeId}`,
                  type: 'system' as const,
                  content: `Error: ${message.message}`,
                  timestamp: new Date(),
                }
              ];
              break;
            }
          }

          updated.set(nodeId, newState);
          return updated;
        });
      });

      // Connect and join room
      service.connect()
        .then(() => {
          setNodeStates(prev => {
            const updated = new Map(prev);
            const current = updated.get(server.id);
            if (current) {
              updated.set(server.id, { ...current, isConnected: true });
            }
            return updated;
          });
          service.joinRoom(selectedRoomId);
        })
        .catch(error => {
          console.error(`Failed to connect to ${server.label}:`, error);
        });

      newNodeStates.set(server.id, state);
    });

    setNodeStates(newNodeStates);
  }, []);

  const handleDisconnect = useCallback(() => {
    nodeStates.forEach(state => {
      state.service.disconnect();
    });
    setServers([]);
    setRoomId(null);
    setNodeStates(new Map());
  }, [nodeStates]);

  const handleSendMessage = useCallback((nodeId: string, message: string) => {
    const state = nodeStates.get(nodeId);
    if (state) {
      state.service.sendMessage(message);
    }
  }, [nodeStates]);

  // Loading state
  if (servers.length === 0) {
    return <ServerSelector onConnect={handleConnect} />;
  }

  return (
    <div className="min-h-screen bg-muted/30 p-4">
      <div className="max-w-7xl mx-auto space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between bg-background rounded-lg p-4 shadow-sm">
          <div>
            <h1 className="text-2xl font-bold">Distributed Chat Demo</h1>
            <p className="text-sm text-muted-foreground">
              Room: <span className="font-mono font-medium">{roomId}</span> •
              {' '}{servers.length} node{servers.length !== 1 ? 's' : ''} connected
            </p>
          </div>
          <Button onClick={handleDisconnect} variant="outline">
            <LogOut className="mr-2 h-4 w-4" />
            Disconnect All
          </Button>
        </div>

        {/* Chat Panels Grid */}
        <div className={`grid gap-4 ${
          servers.length === 1 ? 'grid-cols-1' :
          servers.length === 2 ? 'grid-cols-1 lg:grid-cols-2' :
          'grid-cols-1 lg:grid-cols-2 xl:grid-cols-3'
        }`}>
          {servers.map(server => {
            const state = nodeStates.get(server.id);
            if (!state) return null;

            return (
              <div key={server.id} className="h-[calc(100vh-12rem)]">
                <ChatPanel
                  serverLabel={server.label}
                  port={server.port}
                  roomId={roomId!}
                  userId={state.userId}
                  messages={state.messages}
                  onSendMessage={(msg) => handleSendMessage(server.id, msg)}
                  isConnected={state.isConnected}
                  onlineUsers={state.onlineUsers}
                />
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export default App
