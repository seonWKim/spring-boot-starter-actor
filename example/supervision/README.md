# Actor Supervision Visualizer

An interactive web application to visualize and test actor hierarchies with different supervision strategies.

## Features

- 🎭 **Interactive Actor Hierarchy Visualization** - See your supervisor and worker actors in real-time
- 🎮 **Dynamic Actor Management** - Create, stop, and restart actors through a web UI
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

### 1. Create a Supervisor

1. Enter a supervisor ID (e.g., `supervisor-1`)
2. Click **"🚀 Create Supervisor"**
3. The supervisor will appear in the hierarchy view

### 2. Create Workers

1. Enter a worker ID (e.g., `worker-1`)
2. Select a supervision strategy from the dropdown
3. Click **"👷 Create Worker"**
4. The worker will appear under the selected supervisor

### 3. Send Work to Workers

1. Select a worker by entering its ID
2. Enter a task name (e.g., `process-data`)
3. Click **"📬 Send Work"**
4. Check the logs to see the task being processed

### 4. Test Supervision Strategies

#### Test Restart Strategy:
1. Create a worker with "Restart (unlimited)" strategy
2. Send some work to the worker
3. Click **"💥 Trigger Failure"**
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

### 5. Stop Workers Manually

1. Select a worker by entering its ID
2. Click **"🛑 Stop Worker"**
3. The worker will be gracefully stopped and removed from hierarchy

### 6. Delete Supervisors

1. Select a supervisor by entering its ID
2. Click **"🗑️ Delete Supervisor"**
3. The supervisor and all its workers will be stopped

## API Endpoints

### Supervisor Management

- **POST** `/api/supervisors` - Create a new supervisor
  ```json
  {
    "supervisorId": "supervisor-1"
  }
  ```

- **DELETE** `/api/supervisors/{supervisorId}` - Delete a supervisor

### Worker Management

- **POST** `/api/workers` - Create a new worker
  ```json
  {
    "supervisorId": "supervisor-1",
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

1. **SupervisorActor**: Parent actor that spawns and manages workers with different supervision strategies
2. **WorkerActor**: Child actor that processes tasks and can fail on command
3. **SupervisionController**: REST API for actor management
4. **LogPublisher**: Service for broadcasting logs via SSE
5. **Frontend**: Interactive web UI for visualization and control

### Actor Lifecycle

```
[SupervisorActor]
    │
    ├─[WorkerActor-1] (Restart strategy)
    │   ├─ Spawned with supervision
    │   ├─ Processes work
    │   ├─ Can fail
    │   └─ Restarted by supervisor on failure
    │
    ├─[WorkerActor-2] (Stop strategy)
    │   ├─ Spawned with supervision
    │   ├─ Processes work
    │   ├─ Can fail
    │   └─ Stopped by supervisor on failure
    │
    └─[WorkerActor-3] (Resume strategy)
        ├─ Spawned with supervision
        ├─ Processes work
        ├─ Can fail
        └─ Continues after failure (state preserved)
```

## Learning Objectives

This example demonstrates:

1. **Hierarchical Supervision**: How parent actors supervise child actors
2. **Supervision Strategies**: Different ways to handle actor failures
3. **Actor Lifecycle**: PreRestart, PostStop signals and state management
4. **Spring Integration**: Using Spring DI with actors
5. **Real-time Monitoring**: Streaming logs and visualizing actor hierarchies
6. **Failure Resilience**: How actors recover from failures

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
