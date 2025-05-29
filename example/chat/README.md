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

- Real-time chat using WebSockets
- Clustered architecture using Pekko
- Actor-based message handling
- Metrics collection and export

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
# Build the Docker image from the project root
docker build -t chat-app:latest -f example/chat/Dockerfile .

# Run a container
docker run -p 8080:8080 -p 2551:2551 chat-app:latest
```
