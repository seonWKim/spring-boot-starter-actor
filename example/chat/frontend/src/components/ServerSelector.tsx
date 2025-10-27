import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card"
import { Input } from "./ui/input"
import { Button } from "./ui/button"
import { Badge } from "./ui/badge"
import { Server, Plus, X } from "lucide-react"

interface ServerConfig {
  id: string;
  port: number;
  label: string;
}

interface ServerSelectorProps {
  onConnect: (servers: ServerConfig[], roomId: string) => void;
}

export function ServerSelector({ onConnect }: ServerSelectorProps) {
  const [roomId, setRoomId] = useState("room1");
  const [selectedPorts, setSelectedPorts] = useState<number[]>([8080]);
  const [customPort, setCustomPort] = useState("");

  const commonPorts = [8080, 8081, 8082];

  const togglePort = (port: number) => {
    setSelectedPorts(prev =>
      prev.includes(port)
        ? prev.filter(p => p !== port)
        : [...prev, port].sort((a, b) => a - b)
    );
  };

  const addCustomPort = () => {
    const port = parseInt(customPort);
    if (port > 0 && port < 65536 && !selectedPorts.includes(port)) {
      setSelectedPorts(prev => [...prev, port].sort((a, b) => a - b));
      setCustomPort("");
    }
  };

  const handleConnect = () => {
    if (roomId.trim() && selectedPorts.length > 0) {
      const servers = selectedPorts.map(port => ({
        id: `node-${port}`,
        port,
        label: `Node :${port}`
      }));
      onConnect(servers, roomId.trim());
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen p-4 bg-muted/30">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Server className="h-6 w-6" />
            Spring Actor Chat - Distributed Demo
          </CardTitle>
          <CardDescription>
            Connect to multiple cluster nodes simultaneously to see distributed actors in action
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <label htmlFor="roomId" className="text-sm font-medium">
              Room ID
            </label>
            <Input
              id="roomId"
              value={roomId}
              onChange={(e) => setRoomId(e.target.value)}
              placeholder="Enter room ID"
            />
          </div>

          <div className="space-y-3">
            <label className="text-sm font-medium">
              Select Cluster Nodes (Ports)
            </label>
            <div className="flex flex-wrap gap-2">
              {commonPorts.map(port => (
                <Badge
                  key={port}
                  variant={selectedPorts.includes(port) ? "default" : "outline"}
                  className="cursor-pointer px-4 py-2 text-sm"
                  onClick={() => togglePort(port)}
                >
                  <Server className="h-3 w-3 mr-1" />
                  :{port}
                  {selectedPorts.includes(port) && (
                    <X className="h-3 w-3 ml-1" />
                  )}
                </Badge>
              ))}
            </div>

            <div className="flex gap-2">
              <Input
                type="number"
                value={customPort}
                onChange={(e) => setCustomPort(e.target.value)}
                onKeyPress={(e) => e.key === "Enter" && addCustomPort()}
                placeholder="Custom port (e.g., 9090)"
                className="flex-1"
              />
              <Button onClick={addCustomPort} variant="outline" size="icon">
                <Plus className="h-4 w-4" />
              </Button>
            </div>

            {selectedPorts.length > 0 && (
              <div className="text-sm text-muted-foreground">
                Selected: {selectedPorts.length} node{selectedPorts.length !== 1 ? 's' : ''}
              </div>
            )}
          </div>

          <Button
            onClick={handleConnect}
            disabled={!roomId.trim() || selectedPorts.length === 0}
            className="w-full"
            size="lg"
          >
            Connect to {selectedPorts.length} Node{selectedPorts.length !== 1 ? 's' : ''}
          </Button>

          <div className="bg-muted/50 rounded-lg p-4 text-sm">
            <p className="font-medium mb-2">💡 Tip:</p>
            <ul className="space-y-1 text-muted-foreground">
              <li>• Select multiple nodes to see distributed messaging in action</li>
              <li>• Send messages from any node and watch them appear across all connected nodes</li>
              <li>• Each node shows its own connection status and online users</li>
            </ul>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
