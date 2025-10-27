# Multi-Node Chat Visualization

## Overview

This React frontend demonstrates the power of distributed actor systems by allowing you to connect to multiple cluster nodes simultaneously and visualize how messages flow across them in real-time.

## How It Works

### 1. Server Selection Screen

When you start the app, you see a server selector where you can:
- Choose which cluster nodes to connect to (ports 8080, 8081, 8082 by default)
- Add custom ports if needed
- Select a room ID to join
- Connect to 1, 2, or 3 nodes simultaneously

### 2. Multi-Panel Chat View

Once connected, you see:
- **Side-by-side chat panels** - One for each selected cluster node
- **Independent connections** - Each panel has its own WebSocket connection
- **Synchronized messages** - Messages sent from any node appear in all nodes
- **Per-node status** - See connection status and online users for each node

### 3. Distributed Messaging Demo

This setup perfectly demonstrates:

**Actor Model Benefits:**
- Messages are distributed across cluster nodes automatically
- No Redis or message broker needed for pub/sub
- Actor sharding handles message routing

**Visual Proof:**
1. Connect to nodes on ports 8080, 8081, 8082
2. Send a message from the :8080 panel
3. Watch it instantly appear in :8081 and :8082 panels
4. See that ChatRoomActor distributes messages across the cluster

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Frontend (React App)                       │
├─────────────┬──────────────┬─────────────────────────────┤
│  Panel 1    │   Panel 2    │   Panel 3                  │
│  :8080      │   :8081      │   :8082                    │
│  WS conn 1  │   WS conn 2  │   WS conn 3                │
└──────┬──────┴──────┬───────┴──────┬──────────────────────┘
       │             │              │
       ▼             ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Node 1  │  │  Node 2  │  │  Node 3  │
│  :8080   │  │  :8081   │  │  :8082   │
│          │  │          │  │          │
│ UserActor│  │ UserActor│  │ UserActor│
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │            │             │
     └────────────┼─────────────┘
                  ▼
          ┌──────────────┐
          │ChatRoomActor │  ← Sharded across cluster
          │  (room1)     │
          └──────────────┘
```

## Use Cases

### Demo 1: Basic Distributed Chat
**Setup:** Connect to :8080 and :8081
**Action:** Send messages from both panels
**Result:** Messages appear in both panels, showing cluster synchronization

### Demo 2: Three-Node Cluster
**Setup:** Connect to :8080, :8081, and :8082
**Action:** Send messages from different panels
**Result:** All three panels show all messages, demonstrating full cluster mesh

### Demo 3: Node Failure Simulation
**Setup:** Connect to all three nodes
**Action:** Stop one backend node (e.g., kill port 8081)
**Result:** 
- Panel for :8081 shows "Disconnected"
- Other panels continue working
- Messages still flow through :8080 and :8082

### Demo 4: Different Rooms per Node
**Setup:** Connect :8080 to "room1", :8081 to "room2"
**Action:** Send messages in each room
**Result:** Messages are isolated by room, showing actor partitioning

## Key Components

### `ServerSelector.tsx`
- Multi-port selection UI
- Common ports (8080-8082) + custom port input
- Validates and connects to selected nodes

### `ChatPanel.tsx`
- Individual chat interface per node
- Shows node label, port, and connection status
- Independent message list and input

### `MultiWebSocketService.ts`
- Manages WebSocket connection per node
- Handles reconnection logic
- Routes messages with nodeId for tracking

### `App.tsx`
- Coordinates multiple WebSocket connections
- Maintains separate state per node (messages, users, connection)
- Renders grid layout based on number of connected nodes

## Responsive Grid Layout

- **1 node**: Full width
- **2 nodes**: Side by side on large screens, stacked on mobile
- **3 nodes**: 3-column grid on XL screens, 2-column on large, stacked on mobile

## Development Tips

### Testing Multi-Node Setup

```bash
# Terminal 1: Start 3 backend nodes
./cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Terminal 2: Start frontend dev server
cd frontend
npm run dev

# Browser: Open http://localhost:5173
# Select all three ports: 8080, 8081, 8082
# Join room "room1"
# Send messages and watch them appear across all panels!
```

### Customizing Ports

Edit `ServerSelector.tsx`:
```typescript
const commonPorts = [8080, 8081, 8082, 9090]; // Add more default ports
```

### Styling Adjustments

The grid layout automatically adjusts:
```typescript
// In App.tsx
servers.length === 1 ? 'grid-cols-1' :
servers.length === 2 ? 'grid-cols-1 lg:grid-cols-2' :
'grid-cols-1 lg:grid-cols-2 xl:grid-cols-3'
```

## Future Enhancements

Ideas for extending the multi-node visualization:

1. **Cluster Topology View** - Visual graph showing node connections
2. **Message Flow Animation** - Animate messages moving between nodes
3. **Performance Metrics** - Show latency per node
4. **Node Health Indicators** - CPU/memory usage per node
5. **Actor Distribution View** - Show which actors are on which nodes
6. **Load Balancing Visualization** - See how sharding distributes load
