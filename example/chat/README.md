# Chat Application

This is a sample chat application built with Spring Boot and Pekko (a fork of Akka) for actor-based programming. It demonstrates how to use the Spring Boot Starter Actor library to build a reactive, clustered application.

## Architecture 

```mermaid
graph TD
    subgraph "Cluster"
        subgraph "Node 1"
            WSH1[ChatWebSocketHandler]
            UA1_1[UserActor]
        end

        subgraph "Node 2"
            WSH2[ChatWebSocketHandler]
            UA2_1[UserActor]
        end

        subgraph "Node 3"
            WSH3[ChatWebSocketHandler]
            UA3_1[UserActor]
        end

        CRA[ChatRoomActor]
    end

    %% Relationships
    WS1 <-->|"1:1"| WSH1
    WS2 <-->|"1:1"| WSH2
    WS3 <-->|"1:1"| WSH3

    WSH1 -->|"creates 1:1"| UA1_1
    WSH2 -->|"creates 1:1"| UA2_1
    WSH3 -->|"creates 1:1"| UA3_1

    UA1_1 -->|"N:1"| CRA
    UA2_1 -->|"N:1"| CRA
    UA3_1 -->|"N:1"| CRA

    %% Styling
    classDef local fill:#f9f,stroke:#333,stroke-width:2px;
    classDef cluster fill:#bbf,stroke:#333,stroke-width:2px;
    class UA1_1,UA1_2,UA2_1,UA2_2,UA3_1,UA3_2 local;
    class CRA cluster;
```

## Features

### Backend
- Real-time chat using WebSockets
- Clustered architecture using Pekko
- Actor-based message handling
- Metrics collection and export

### Frontend (React)
- Modern React 19 + TypeScript + Vite
- Tailwind CSS + shadcn/ui components
- Real-time WebSocket communication
- Online user presence tracking
- Connection status indicator
- Responsive, accessible design

## Running Locally

You can run multiple instances of the application locally using the provided `cluster-start.sh` script:

```bash
./cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

This will start 3 instances of the application with the following configuration:
- Instance 1: HTTP port 8080, Pekko port 2551
- Instance 2: HTTP port 8081, Pekko port 2552
- Instance 3: HTTP port 8082, Pekko port 2553

## Running with Docker

Build and run the application using Docker:

```bash
sh init-local-docker.sh
```

## Frontend Development

The chat application includes a modern React frontend located in the `frontend/` directory.

### Quick Start

```bash
# From the chat example directory
cd frontend

# Install dependencies (first time only)
npm install

# Start development server
npm run dev
```

The development server will start on `http://localhost:5173` with automatic proxy to the backend WebSocket.

### Building for Production

```bash
# Build React app (outputs to src/main/resources/static)
cd frontend
npm run build

# Then run the Spring Boot application
cd ..
./gradlew bootRun
```

The built frontend is automatically served by Spring Boot at `http://localhost:8080`

### Frontend Tech Stack

- **React 19** - Latest React with improved hooks and concurrency
- **TypeScript** - Type-safe development
- **Vite** - Lightning-fast HMR and builds
- **Tailwind CSS v4** - Utility-first styling
- **shadcn/ui** - Beautiful, accessible components
- **Lucide React** - Modern icon library

### Frontend Features

✅ **Online Presence** - See who's connected to the chat room
✅ **Real-time Messaging** - Instant message delivery via WebSocket
✅ **Connection Status** - Visual indicator of WebSocket connection
✅ **Auto-reconnect** - Automatic reconnection on disconnect
✅ **Responsive Design** - Works on desktop and mobile

See `frontend/README.md` for more details on the frontend architecture.
