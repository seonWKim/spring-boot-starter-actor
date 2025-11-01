# Actor Supervision Tree Visualizer

An interactive web application with visual tree diagram to visualize and test actor hierarchies with different supervision strategies.

## Features

- 🌳 **Multi-Level Hierarchical Tree** - B+ tree-like visualization with unlimited depth
- ⭕ **Circle Nodes** - Each actor represented by a colored circle showing only its ID
- 🔗 **Connection Lines** - Curved SVG paths showing parent-child relationships at all levels
- 🔄 **Dynamic Hierarchy Querying** - Uses `ctx.getChildren()` to reflect real-time actor state
- 💡 **Hover Details** - Detailed information and actions appear when hovering over nodes
- 🎮 **Interactive Actions** - Add children, send work, trigger failures, stop actors directly from hover cards
- 👶 **Workers Can Have Children** - Any actor (not just supervisors) can spawn and supervise child actors
- 📊 **Multiple Supervision Strategies**:
  - **Restart (unlimited)**: Restarts failed actors indefinitely
  - **Restart (limited)**: Restarts failed actors up to 3 times per minute
  - **Stop**: Terminates failed actors permanently
  - **Resume**: Ignores failures and continues processing
- 📝 **Live Log Streaming** - View actor lifecycle events in real-time via Server-Sent Events
- 💥 **Failure Testing** - Trigger intentional failures to see supervision strategies in action
- 📬 **Work Distribution** - Send tasks to specific workers and track completion

## Running the Application

### Using Gradle

```bash
./gradlew :example:supervision:bootRun
```

### Using IDE

Run the `SupervisionApplication` main class.

### Access the Web UI

Open your browser and navigate to:
```
http://localhost:8080
```

## How to Use

### Visual Tree Structure

The application displays actors as a tree diagram:
- **🔵 Blue circles** = Supervisor actors (root-supervisor)
- **🟢 Green circles** = Worker actors
- **Gray curved lines** = Parent-child relationships at all levels
- Circles show only the actor ID (truncated if long)
- **Multi-level hierarchy** = Workers can have their own children, creating unlimited depth

### 1. Add a Child Actor

1. **Hover** over any circle (supervisor or worker)
2. Click **"➕ Add Child"** in the hover card
3. Select a supervision strategy (worker ID is auto-generated)
4. Click **"Add Child"**
5. New child appears as a green circle below its parent with a random 6-character ID

**Auto-Generated IDs**: Worker IDs are automatically generated as random 6-character alphanumeric strings (e.g., `abc123`, `x7y2k9`). This simplifies the UI and prevents naming conflicts.

**Note**: Any actor can spawn children, not just supervisors! Workers are created under their actual parent (not always the root supervisor). You can create deep hierarchies like:
```
root-supervisor
  ├─ abc123 (worker)
  │   ├─ x7y2k9 (worker)
  │   └─ m4n8p1 (worker)
  └─ q5w3e7 (worker)
      └─ z9x2c6 (worker)
          └─ b3v7n4 (worker)
```

### 2. Send Work to a Worker

1. **Hover** over any worker circle (green)
2. Click **"📬 Send Work"** in the hover card
3. Enter a task name when prompted
4. Check the logs panel to see the task being processed

### 3. View Actor Details

**Hover** over any circle to see:
- Full actor ID
- Actor type (supervisor/worker)
- Supervision strategy (for workers)
- Actor path
- Available actions

### 4. Test Supervision Strategies

#### Test Restart Strategy:
1. Add a worker with "Restart (unlimited)" strategy
2. Hover over the worker, click **"📬 Send Work"** a few times
3. Hover over the worker, click **"💥 Trigger Failure"**
4. Observe in the logs:
   - Worker fails with an error
   - Supervisor restarts the worker
   - Worker is ready to process new tasks
   - Previous state (task count) is lost

#### Test Stop Strategy:
1. Create a worker with "Stop on failure" strategy
2. Send some work to the worker
3. Click **"💥 Trigger Failure"**
4. Observe in the logs:
   - Worker fails with an error
   - Supervisor stops the worker permanently
   - Worker disappears from the hierarchy

#### Test Resume Strategy:
1. Create a worker with "Resume (ignore failure)" strategy
2. Send some work to the worker
3. Click **"💥 Trigger Failure"**
4. Observe in the logs:
   - Worker fails with an error
   - Supervisor ignores the failure
   - Worker continues processing (state preserved)

#### Test Limited Restart:
1. Create a worker with "Restart (limited: 3 times/min)" strategy
2. Trigger failures multiple times rapidly (more than 3)
3. Observe in the logs:
   - Worker restarts for the first 3 failures
   - After exceeding the limit, the worker is stopped

### 5. Stop a Worker

1. Hover over any worker circle
2. Click **"🛑 Stop"** in the hover card
3. Confirm the action
4. Worker circle disappears from the tree

## API Endpoints

### Supervisor Management

- **POST** `/api/supervisors` - Create a new supervisor
  ```json
  {
    "supervisorId": "supervisor-1"
  }
  ```

- **DELETE** `/api/supervisors/{supervisorId}` - Delete a supervisor

### Child Actor Management (New)

- **POST** `/api/actors/{parentId}/children` - Create a child under any parent (supervisor or worker)
  ```json
  {
    "childId": "abc123",
    "strategy": "restart",
    "parentType": "worker",
    "parentPath": "akka://SpringActorSystem/user/root-supervisor/worker-1"
  }
  ```
  - `parentType`: `"supervisor"` or `"worker"`
  - `parentPath`: Required for workers (actor path)
  - `strategy`: `"restart"`, `"restart-limited"`, `"stop"`, or `"resume"`
  - This endpoint routes spawn requests through the actor hierarchy

### Worker Management (Legacy)

- **POST** `/api/workers` - Create a new worker under root supervisor
  ```json
  {
    "workerId": "worker-1",
    "strategy": "restart"
  }
  ```

- **DELETE** `/api/workers/{workerId}?supervisorId={supervisorId}` - Stop a worker

### Worker Operations

- **POST** `/api/workers/{workerId}/work` - Send work to a worker
  ```json
  {
    "supervisorId": "supervisor-1",
    "taskName": "process-data"
  }
  ```

- **POST** `/api/workers/{workerId}/fail` - Trigger a failure in a worker
  ```json
  {
    "supervisorId": "supervisor-1"
  }
  ```

### Hierarchy

- **GET** `/api/hierarchy` - Get the current actor hierarchy

### Logs

- **GET** `/api/logs/stream` - Server-Sent Events stream for real-time logs

## Architecture

### Components

1. **SupervisorActor**: Root actor that spawns and manages child actors with different supervision strategies
2. **WorkerActor**: Actor that processes tasks, can fail on command, and can spawn its own children
3. **ActorHierarchy**: Shared data structures for recursive hierarchy management
4. **SupervisionController**: REST API for actor management with recursive hierarchy traversal
5. **LogPublisher**: Service for broadcasting logs via SSE
6. **Frontend**: Interactive web UI with recursive tree visualization

### Key Features

- **Dynamic Hierarchy Querying**: Uses `ctx.getChildren()` to query actual actor state instead of manually tracking
- **Recursive Hierarchy**: Both SupervisorActor and WorkerActor can spawn children, creating unlimited depth
- **Async Hierarchy Traversal**: Uses `CompletableFuture` to recursively ask all children for their hierarchy
- **Recursive Child Spawning**: Uses `RouteSpawnChild` command that broadcasts through the tree to find the correct parent at any depth
  - Supervisor receives spawn request → checks if it's the parent → if not, broadcasts to all children
  - Each child recursively checks and forwards until the parent is found
  - The parent actor spawns the child and sends success response
  - If parent not found, leaf nodes send error responses

### Actor Lifecycle (Multi-Level Example)

```
[root-supervisor] (Supervisor)
    │
    ├─[worker-1] (Restart strategy)
    │   ├─ Spawned with supervision
    │   ├─ Processes work
    │   ├─ Can spawn own children
    │   ├─ Can fail → Restarted by parent
    │   │
    │   └─[worker-1-1] (Stop strategy)
    │       ├─ Spawned by worker-1
    │       ├─ Processes work
    │       └─ Can fail → Stopped by worker-1
    │
    └─[worker-2] (Resume strategy)
        ├─ Spawned with supervision
        ├─ Processes work
        ├─ Can spawn own children
        └─ Can fail → Continues (state preserved)
```

## Learning Objectives

This example demonstrates:

1. **Multi-Level Hierarchical Supervision**: How actors supervise child actors at any depth
2. **Supervision Strategies**: Different ways to handle actor failures at each level
3. **Actor Lifecycle**: PreRestart, PostStop signals and state management across hierarchies
4. **Spring Integration**: Using Spring DI with actors and hierarchical spawning
5. **Real-time Monitoring**: Streaming logs and visualizing complex actor hierarchies
6. **Failure Resilience**: How actors recover from failures and how failures propagate through hierarchies
7. **Dynamic State Querying**: Using `ctx.getChildren()` to query real actor state instead of manual tracking
8. **Recursive Actor Patterns**: Workers that can themselves supervise other workers
9. **Recursive Message Routing**: Broadcasting messages through the hierarchy to find the target actor at arbitrary depth
10. **Auto-Generated IDs**: Simplifying UX by automatically generating unique actor identifiers

## Tips

- **Monitor the Logs**: The logs panel shows detailed information about actor lifecycle events
- **Experiment with Strategies**: Try different supervision strategies to understand their behavior
- **Trigger Multiple Failures**: Test the restart-limited strategy by triggering failures rapidly
- **Check the Hierarchy**: Use the refresh button or wait for auto-refresh to see the current state
- **Cross-reference**: Compare the frontend hierarchy with the server logs to verify behavior

## Troubleshooting

- **Port 8080 already in use**: Change the port in `application.yml`
- **Logs not streaming**: Check browser console for SSE connection errors
- **Worker not found**: Make sure you created the supervisor first
- **Build errors**: Run `./gradlew clean build` to rebuild

## Next Steps

- Try creating multiple supervisors with different workers
- Experiment with different failure patterns
- Monitor how state is preserved or lost with different strategies
- Build your own supervision hierarchies for your use cases
