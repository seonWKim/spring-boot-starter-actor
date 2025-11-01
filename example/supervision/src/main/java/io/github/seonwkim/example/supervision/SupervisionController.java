package io.github.seonwkim.example.supervision;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.HashMap;
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
    private final Map<String, SpringActorRef<HierarchicalActor.Command>> supervisors =
            new ConcurrentHashMap<>();

    public SupervisionController(SpringActorSystem actorSystem, LogPublisher logPublisher) {
        this.actorSystem = actorSystem;
        this.logPublisher = logPublisher;
        // Create default supervisor on startup
        createDefaultSupervisor();
    }

    private void createDefaultSupervisor() {
        try {
            SpringActorRef<HierarchicalActor.Command> supervisor =
                    actorSystem
                            .actor(SupervisorActor.class)
                            .withId("supervisor")
                            .withTimeout(Duration.ofSeconds(5))
                            .startAndWait();
            supervisors.put("supervisor", supervisor);
            logPublisher.publish("System initialized with supervisor");
        } catch (Exception e) {
            System.err.println("Failed to create default supervisor: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
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
                    .body(Map.of(
                                    "success",
                                    false,
                                    "message",
                                    "Supervisor already exists: " + supervisorId));
        }

        try {
            SpringActorRef<HierarchicalActor.Command> supervisor =
                    (SpringActorRef<HierarchicalActor.Command>) (SpringActorRef<?>) actorSystem
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
     * Create a new child actor under any parent (supervisor or worker).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostMapping("/actors/{parentId}/children")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createChild(
            @PathVariable String parentId,
            @RequestBody Map<String, String> request) {
        String childId = request.get("childId");
        String strategy = request.getOrDefault("strategy", "restart");
        String parentType = request.get("parentType"); // "supervisor" or "worker"
        String parentPath = request.get("parentPath"); // Full actor path

        if (childId == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "childId is required")));
        }

        // Check if parent is the root supervisor
        if ("supervisor".equals(parentId) || "supervisor".equals(parentType)) {
            SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get(parentId);
            if (supervisor == null) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest()
                                .body(Map.of("success", false, "message", "Supervisor not found")));
            }

            return supervisor
                    .ask(
                            (org.apache.pekko.actor.typed.ActorRef<ActorHierarchy.SpawnResult> replyTo) ->
                                    new HierarchicalActor.SpawnChild(childId, strategy, replyTo))
                    .toCompletableFuture()
                    .thenApply(
                            resultObj -> {
                                ActorHierarchy.SpawnResult result = (ActorHierarchy.SpawnResult) resultObj;
                                if (result.success) {
                                    return ResponseEntity.ok(
                                            toMap(
                                                    "success",
                                                    true,
                                                    "childId",
                                                    childId,
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
                                                            "Failed to create child: " + ex.getMessage())));
        } else {
            // Parent is a worker - route through root supervisor
            SpringActorRef<HierarchicalActor.Command> rootSupervisor = supervisors.get("supervisor");
            if (rootSupervisor == null) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.badRequest()
                                .body(Map.of("success", false, "message", "Root supervisor not found")));
            }

            return rootSupervisor
                    .ask((org.apache.pekko.actor.typed.ActorRef<ActorHierarchy.SpawnResult> replyTo) ->
                            new HierarchicalActor.RouteSpawnChild(parentId, childId, strategy, replyTo))
                    .toCompletableFuture()
                    .thenApply(
                            resultObj -> {
                                ActorHierarchy.SpawnResult result = (ActorHierarchy.SpawnResult) resultObj;
                                if (result.success) {
                                    return ResponseEntity.ok(
                                            toMap(
                                                    "success",
                                                    true,
                                                    "childId",
                                                    childId,
                                                    "strategy",
                                                    strategy,
                                                    "message",
                                                    result.message));
                                } else {
                                    return ResponseEntity.badRequest()
                                            .body(toMap("success", false, "message", result.message));
                                }
                            })
                    .exceptionally(ex ->
                            ResponseEntity.internalServerError()
                                    .body(
                                            Map.of(
                                                    "success",
                                                    false,
                                                    "message",
                                                    "Failed to create child: " + ex.getMessage())));
        }
    }

    /**
     * Create a new worker under a supervisor (legacy endpoint for backward compatibility).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @PostMapping("/workers")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createWorker(
            @RequestBody Map<String, String> request) {
        String supervisorId = request.getOrDefault("supervisorId", "supervisor");
        String workerId = request.get("workerId");
        String strategy = request.getOrDefault("strategy", "restart");

        if (workerId == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "workerId is required")));
        }

        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<ActorHierarchy.SpawnResult> replyTo) ->
                                new HierarchicalActor.SpawnChild(workerId, strategy, replyTo))
                .toCompletableFuture()
                .thenApply(
                        resultObj -> {
                            ActorHierarchy.SpawnResult result = (ActorHierarchy.SpawnResult) resultObj;
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

        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<HierarchicalActor.WorkResult> replyTo) ->
                                new HierarchicalActor.RouteToChild(workerId, taskName, replyTo))
                .toCompletableFuture()
                .thenApply(
                        resultObj -> {
                            HierarchicalActor.WorkResult result = (HierarchicalActor.WorkResult) resultObj;
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

        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<String> replyTo) ->
                                new HierarchicalActor.TriggerChildFailure(workerId, replyTo))
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

        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get(supervisorId);
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Supervisor not found")));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<String> replyTo) ->
                                new HierarchicalActor.StopChild(workerId, replyTo))
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
        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.remove(supervisorId);

        if (supervisor == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Supervisor not found"));
        }

        supervisor.stop();
        return ResponseEntity.ok(
                toMap("success", true, "supervisorId", supervisorId, "message", "Supervisor stopped"));
    }

    /**
     * Get the current actor hierarchy (recursive).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @GetMapping("/hierarchy")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getHierarchy() {
        if (supervisors.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(toMap("root", null)));
        }

        // Get the root supervisor
        SpringActorRef<HierarchicalActor.Command> supervisor = supervisors.get("supervisor");
        if (supervisor == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(toMap("root", null)));
        }

        return supervisor
                .ask(
                        (org.apache.pekko.actor.typed.ActorRef<ActorHierarchy.ActorNode> replyTo) ->
                                new HierarchicalActor.GetHierarchy(replyTo))
                .toCompletableFuture()
                .thenApply(
                        nodeObj -> {
                            ActorHierarchy.ActorNode node = (ActorHierarchy.ActorNode) nodeObj;
                            return ResponseEntity.ok(toMap("root", convertNodeToMap(node)));
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
     * Recursively convert ActorNode to Map for JSON serialization.
     */
    private Map<String, Object> convertNodeToMap(ActorHierarchy.ActorNode node) {
        List<Map<String, Object>> childrenMaps = node.children.stream()
                .map(this::convertNodeToMap)
                .collect(Collectors.toList());

        return toMap(
                "actorId", node.actorId,
                "actorType", node.actorType,
                "strategy", node.strategy,
                "path", node.path,
                "failureCount", node.failureCount,
                "children", childrenMaps
        );
    }

    /**
     * SSE endpoint for streaming actor logs.
     */
    @GetMapping("/logs/stream")
    public SseEmitter streamLogs() {
        return logPublisher.register();
    }
}
