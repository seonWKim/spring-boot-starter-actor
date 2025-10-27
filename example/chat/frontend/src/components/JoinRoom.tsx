import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card"
import { Input } from "./ui/input"
import { Button } from "./ui/button"
import { LogIn } from "lucide-react"

interface JoinRoomProps {
  onJoinRoom: (roomId: string) => void;
  disabled?: boolean;
}

export function JoinRoom({ onJoinRoom, disabled }: JoinRoomProps) {
  const [roomId, setRoomId] = useState("room1");

  const handleJoin = () => {
    if (roomId.trim()) {
      onJoinRoom(roomId.trim());
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Spring Actor Chat</CardTitle>
          <CardDescription>
            Built with Spring Boot + Pekko Actors for distributed messaging
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <label htmlFor="roomId" className="text-sm font-medium">
              Room ID
            </label>
            <Input
              id="roomId"
              value={roomId}
              onChange={(e) => setRoomId(e.target.value)}
              onKeyPress={(e) => e.key === "Enter" && handleJoin()}
              placeholder="Enter room ID"
              disabled={disabled}
            />
          </div>
          <Button
            onClick={handleJoin}
            disabled={disabled || !roomId.trim()}
            className="w-full"
          >
            <LogIn className="mr-2 h-4 w-4" />
            Join Room
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
