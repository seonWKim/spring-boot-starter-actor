package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller for managing actor hierarchies and testing supervision strategies.
 */
@RestController
@RequestMapping("/api")
public class SupervisionController {

    private final SpringActorSystem actorSystem;
    private final LogPublisher logPublisher;
    private final Map<String, SpringActorRef<SupervisorActor.Command>> supervisors =
            new ConcurrentHashMap<>();

    public SupervisionController(SpringActorSystem actorSystem, LogPublisher logPublisher) {
        this.actorSystem = actorSystem;
        this.logPublisher = logPublisher;
        // Create default supervisor on startup
        createDefaultSupervisor();
    }

    private void createDefaultSupervisor() {
        try {
            SpringActorRef<SupervisorActor.Command> supervisor =
                    actorSystem
                            .actor(SupervisorActor.class)
                            .withId("root-supervisor")
                            .withTimeout(Duration.ofSeconds(5))
                            .startAndWait();
            supervisors.put("root-supervisor", supervisor);
            logPublisher.publish("System initialized with root-supervisor");
        } catch (Exception e) {
            System.err.println("Failed to create default supervisor: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object... keyValues) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /**
     * Create a new supervisor actor.
     */
    @PostMapping("/supervisors")
    public ResponseEntity<Map<String, Object>> createSupervisor(
            @RequestBody Map<String, String> request) {
        String supervisorId = request.get("supervisorId");

        if (supervisorId == null || supervisorId.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "supervisorId is required"));
        }

        if (supervisors.containsKey(supervisorId)) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Supervisor already exists: " + supervisorId));
        }

        try {
            SpringActorRef<SupervisorActor.Command> supervisor =
                    actorSystem
                            .actor(SupervisorActor.class)
                            .withId(supervisorId)
                            .withTimeout(Duration.ofSeconds(5))
                            .startAndWait();

            supervisors.put(supervisorId, supervisor);

            return ResponseEntity.ok(
                    toMap(
                            "success",
                            true,
                            "supervisorId",
                            supervisorId,
                            "message",
                            "Supervisor created successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Failed to create supervisor: " + e.getMessage()));
        }
    }

    /**
     * Create a new worker under a supervisor.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostMapping("/workers")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createWorker(
            @RequestBody Map<String, String> request) {
        String supervisorId = request.get("supervisorId");
        String workerId = request.get("workerId");
        String strategy = request.getOrDefault("strategy", "restart");

        if (supervisorId == null || workerId == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(
                                    Map.of(
                                            "success",
                                            false,
                                            "message",
                                            "supervisorId and workerId are required")));
        }

        SpringActorRef<SupervisorActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<SupervisorActor.SpawnResult> replyTo) ->
                                new SupervisorActor.SpawnWorker(workerId, strategy, replyTo))
                .toCompletableFuture()
                .thenApply(
                        resultObj -> {
                            SupervisorActor.SpawnResult result = (SupervisorActor.SpawnResult) resultObj;
                            if (result.success) {
                                return ResponseEntity.ok(
                                        toMap(
                                                "success",
                                                true,
                                                "workerId",
                                                workerId,
                                                "strategy",
                                                strategy,
                                                "message",
                                                result.message));
                            } else {
                                return ResponseEntity.badRequest()
                                        .body(toMap("success", false, "message", result.message));
                            }
                        })
                .exceptionally(
                        ex ->
                                ResponseEntity.internalServerError()
                                        .body(
                                                Map.of(
                                                        "success",
                                                        false,
                                                        "message",
                                                        "Failed to create worker: " + ex.getMessage())));
    }

    /**
     * Send work to a specific worker.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostMapping("/workers/{workerId}/work")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendWork(
            @PathVariable String workerId, @RequestBody Map<String, String> request) {
        String supervisorId = request.get("supervisorId");
        String taskName = request.getOrDefault("taskName", "default-task");

        SpringActorRef<SupervisorActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<WorkerActor.WorkResult> replyTo) ->
                                new SupervisorActor.RouteWork(workerId, taskName, replyTo))
                .toCompletableFuture()
                .thenApply(
                        resultObj -> {
                            WorkerActor.WorkResult result = (WorkerActor.WorkResult) resultObj;
                            return ResponseEntity.ok(
                                    toMap(
                                            "success",
                                            true,
                                            "workerId",
                                            result.workerId,
                                            "taskName",
                                            result.taskName,
                                            "tasksCompleted",
                                            result.tasksCompleted));
                        })
                .exceptionally(
                        ex ->
                                ResponseEntity.internalServerError()
                                        .body(
                                                Map.of(
                                                        "success",
                                                        false,
                                                        "message",
                                                        "Failed to send work: " + ex.getMessage())));
    }

    /**
     * Trigger a failure in a specific worker.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostMapping("/workers/{workerId}/fail")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> triggerFailure(
            @PathVariable String workerId, @RequestBody Map<String, String> request) {
        String supervisorId = request.get("supervisorId");

        SpringActorRef<SupervisorActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<String> replyTo) ->
                                new SupervisorActor.TriggerWorkerFailure(workerId, replyTo))
                .toCompletableFuture()
                .thenApply(
                        result ->
                                ResponseEntity.ok(
                                        toMap(
                                                "success",
                                                true,
                                                "workerId",
                                                workerId,
                                                "message",
                                                result)))
                .exceptionally(
                        ex ->
                                ResponseEntity.internalServerError()
                                        .body(
                                                Map.of(
                                                        "success",
                                                        false,
                                                        "message",
                                                        "Failed to trigger failure: " + ex.getMessage())));
    }

    /**
     * Stop a specific worker.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @DeleteMapping("/workers/{workerId}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> stopWorker(
            @PathVariable String workerId, @RequestParam String supervisorId) {

        SpringActorRef<SupervisorActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<String> replyTo) ->
                                new SupervisorActor.StopWorker(workerId, replyTo))
                .toCompletableFuture()
                .thenApply(
                        result ->
                                ResponseEntity.ok(
                                        toMap(
                                                "success",
                                                true,
                                                "workerId",
                                                workerId,
                                                "message",
                                                result)))
                .exceptionally(
                        ex ->
                                ResponseEntity.internalServerError()
                                        .body(
                                                Map.of(
                                                        "success",
                                                        false,
                                                        "message",
                                                        "Failed to stop worker: " + ex.getMessage())));
    }

    /**
     * Delete a supervisor (and all its workers).
     */
    @DeleteMapping("/supervisors/{supervisorId}")
    public ResponseEntity<Map<String, Object>> deleteSupervisor(
            @PathVariable String supervisorId) {
        SpringActorRef<SupervisorActor.Command> supervisor = supervisors.remove(supervisorId);

        if (supervisor == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Supervisor not found"));
        }

        supervisor.stop();
        return ResponseEntity.ok(
                toMap("success", true, "supervisorId", supervisorId, "message", "Supervisor stopped"));
    }

    /**
     * Get the current actor hierarchy.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @GetMapping("/hierarchy")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getHierarchy() {
        if (supervisors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(toMap("supervisors", List.of())));
        }

        List<CompletableFuture<Map<String, Object>>> futures =
                supervisors.entrySet().stream()
                        .map(
                                entry -> {
                                    String supervisorId = entry.getKey();
                                    SpringActorRef<SupervisorActor.Command> supervisor =
                                            entry.getValue();

                                    return supervisor
                                            .ask(
                                                    (org.apache.pekko.actor.typed.ActorRef<SupervisorActor.HierarchyInfo> replyTo) ->
                                                            new SupervisorActor.GetHierarchy(replyTo))
                                            .toCompletableFuture()
                                            .thenApply(
                                                    hierarchyObj -> {
                                                        SupervisorActor.HierarchyInfo hierarchy =
                                                                (SupervisorActor.HierarchyInfo) hierarchyObj;
                                                        return Map.<String, Object>of(
                                                                "supervisorId",
                                                                supervisorId,
                                                                "workers",
                                                                hierarchy.workers.stream()
                                                                        .map(
                                                                                w ->
                                                                                        Map
                                                                                                .<
                                                                                                        String,
                                                                                                        Object>
                                                                                                        of(
                                                                                                                "workerId",
                                                                                                                w
                                                                                                                        .workerId,
                                                                                                                "strategy",
                                                                                                                w
                                                                                                                        .strategy,
                                                                                                                "path",
                                                                                                                w
                                                                                                                        .path))
                                                                        .collect(
                                                                                Collectors
                                                                                        .toList()));
                                                    });
                                })
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(
                        v -> {
                            List<Map<String, Object>> supervisorList =
                                    futures.stream()
                                            .map(CompletableFuture::join)
                                            .collect(Collectors.toList());
                            return ResponseEntity.ok(toMap("supervisors", supervisorList));
                        })
                .exceptionally(
                        ex ->
                                ResponseEntity.internalServerError()
                                        .body(
                                                Map.of(
                                                        "error",
                                                        "Failed to get hierarchy: " + ex.getMessage())));
    }

    /**
     * SSE endpoint for streaming actor logs.
     */
    @GetMapping("/logs/stream")
    public SseEmitter streamLogs() {
        return logPublisher.register();
    }
}
