package io.github.seonwkim.example.virtualthreads;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class VirtualThreadTestController {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadTestController.class);
    private final SpringActorSystem actorSystem;

    public VirtualThreadTestController(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Simple API to test if actors run on virtual threads with custom virtual-thread-executor
     * GET /api/virtual
     */
    @GetMapping("/virtual")
    public Map<String, Object> testVirtual() {
        log.info("Testing virtual-thread-executor dispatcher");

        SpringActorRef<VirtualThreadTestActor.Command> actor = actorSystem
                .actor(VirtualThreadTestActor.class)
                .withId("virtual-actor-" + System.currentTimeMillis())
                .withDispatcherFromConfig("virtual-threads-dispatcher")
                .spawnAndWait();

        actor.tell(new VirtualThreadTestActor.CheckThread("virtual-thread-executor"));

        Map<String, Object> response = new HashMap<>();
        response.put("dispatcher", "virtual-threads-dispatcher");
        response.put("executor", "virtual-thread-executor");
        response.put("description", "Uses custom dispatcher with virtual-thread-executor");
        response.put("message", "Check logs to see if thread is virtual");
        return response;
    }
}
