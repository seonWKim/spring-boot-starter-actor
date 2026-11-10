package io.github.seonwkim.example.virtualthreads;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class VirtualThreadTestController {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadTestController.class);

    /** One actor for all requests, spawned on the first one without blocking the request thread. */
    private final Mono<SpringActorHandle<VirtualThreadTestActor.Command>> actor;

    public VirtualThreadTestController(SpringActorSystem actorSystem) {
        this.actor = Mono.fromCompletionStage(() -> actorSystem
                        .actor(VirtualThreadTestActor.class)
                        .withId("virtual-actor")
                        .withDispatcherFromConfig("virtual-threads-dispatcher")
                        .spawn())
                .cache();
    }

    /**
     * Simple API to test if actors run on virtual threads with custom virtual-thread-executor
     * GET /api/virtual
     */
    @GetMapping("/virtual")
    public Mono<Map<String, Object>> testVirtual() {
        log.info("Testing virtual-thread-executor dispatcher");

        return actor.map(handle -> {
            handle.tell(new VirtualThreadTestActor.CheckThread("virtual-thread-executor"));

            Map<String, Object> response = new HashMap<>();
            response.put("dispatcher", "virtual-threads-dispatcher");
            response.put("executor", "virtual-thread-executor");
            response.put("description", "Uses custom dispatcher with virtual-thread-executor");
            response.put("message", "Check logs to see if thread is virtual");
            return response;
        });
    }
}
