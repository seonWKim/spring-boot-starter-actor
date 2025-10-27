import { MessageList } from "./MessageList"
import { MessageInput } from "./MessageInput"
import type { MessageData } from "./Message"
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card"
import { Badge } from "./ui/badge"
import { Wifi, WifiOff, Users } from "lucide-react"

interface ChatPanelProps {
  serverLabel: string;
  port: number;
  roomId: string;
  userId: string | null;
  messages: MessageData[];
  onSendMessage: (message: string) => void;
  isConnected: boolean;
  onlineUsers?: Set<string>;
}

export function ChatPanel({
  serverLabel,
  port,
  roomId: _roomId,
  userId,
  messages,
  onSendMessage,
  isConnected,
  onlineUsers = new Set(),
}: ChatPanelProps) {
  return (
    <Card className="flex flex-col h-full">
      <CardHeader className="border-b py-3 px-4">
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg flex items-center gap-2">
            {serverLabel}
            <Badge variant="outline" className="font-mono text-xs">
              :{port}
            </Badge>
          </CardTitle>
          <Badge variant={isConnected ? "default" : "destructive"} className="flex items-center gap-1">
            {isConnected ? (
              <>
                <Wifi className="h-3 w-3" />
                <span className="hidden sm:inline">Connected</span>
              </>
            ) : (
              <>
                <WifiOff className="h-3 w-3" />
                <span className="hidden sm:inline">Disconnected</span>
              </>
            )}
          </Badge>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground mt-1">
          <Users className="h-3 w-3" />
          <span>{onlineUsers.size} online</span>
          {userId && (
            <>
              <span>•</span>
              <span>ID: {userId.substring(0, 8)}...</span>
            </>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex-1 flex flex-col p-0 overflow-hidden">
        <MessageList messages={messages} />
        <MessageInput onSendMessage={onSendMessage} disabled={!isConnected} />
      </CardContent>
    </Card>
  );
}
