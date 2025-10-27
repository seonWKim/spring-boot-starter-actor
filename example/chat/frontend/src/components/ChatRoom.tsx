import { MessageList } from "./MessageList"
import { MessageInput } from "./MessageInput"
import type { MessageData } from "./Message"
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card"
import { Button } from "./ui/button"
import { Badge } from "./ui/badge"
import { LogOut, Users, Wifi, WifiOff } from "lucide-react"

interface ChatRoomProps {
  roomId: string;
  userId: string;
  messages: MessageData[];
  onSendMessage: (message: string) => void;
  onLeaveRoom: () => void;
  isConnected: boolean;
  onlineUsers?: Set<string>;
}

export function ChatRoom({
  roomId,
  userId,
  messages,
  onSendMessage,
  onLeaveRoom,
  isConnected,
  onlineUsers = new Set(),
}: ChatRoomProps) {
  return (
    <div className="flex items-center justify-center min-h-screen p-4 bg-muted/30">
      <Card className="w-full max-w-4xl h-[90vh] flex flex-col">
        <CardHeader className="border-b">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <CardTitle className="text-2xl">Chat Room: {roomId}</CardTitle>
              <Badge variant={isConnected ? "default" : "destructive"} className="flex items-center gap-1">
                {isConnected ? (
                  <>
                    <Wifi className="h-3 w-3" />
                    Connected
                  </>
                ) : (
                  <>
                    <WifiOff className="h-3 w-3" />
                    Disconnected
                  </>
                )}
              </Badge>
            </div>
            <Button onClick={onLeaveRoom} variant="outline" size="sm">
              <LogOut className="mr-2 h-4 w-4" />
              Leave Room
            </Button>
          </div>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Users className="h-4 w-4" />
            <span>{onlineUsers.size} user{onlineUsers.size !== 1 ? 's' : ''} online</span>
            <span className="text-xs">• Your ID: {userId.substring(0, 8)}...</span>
          </div>
        </CardHeader>
        <CardContent className="flex-1 flex flex-col p-0 overflow-hidden">
          <MessageList messages={messages} />
          <MessageInput onSendMessage={onSendMessage} disabled={!isConnected} />
        </CardContent>
      </Card>
    </div>
  );
}
