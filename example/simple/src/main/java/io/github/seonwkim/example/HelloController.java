package io.github.seonwkim.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST controller that exposes the HelloActor functionality via HTTP endpoints. Provides a simple
 * API for interacting with the actor system.
 */
@RestController
public class HelloController {

    private final HelloService helloService;

    /**
     * Creates a new HelloController with the given service.
     *
     * @param helloService The service for interacting with HelloActors
     */
    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    /**
     * Endpoint to send a hello message to the actor and get a response. This is a simple GET endpoint
     * that requires no parameters.
     *
     * @return A Mono containing the response from the actor
     */
    @GetMapping("/hello")
    public Mono<String> hello() {
        return helloService.hello();
    }

    /**
     * Endpoint to trigger a failure in the actor, causing it to restart.
     * This demonstrates the PreRestart lifecycle hook.
     *
     * @return A Mono containing confirmation that the restart was triggered
     */
    @GetMapping("/hello/restart")
    public Mono<String> triggerRestart() {
        return helloService.triggerRestart();
    }

    /**
     * Endpoint to stop the actor gracefully.
     * This demonstrates the PostStop lifecycle hook.
     *
     * @return A Mono containing confirmation that the actor was stopped
     */
    @GetMapping("/hello/stop")
    public Mono<String> stopActor() {
        return helloService.stopActor();
    }
}
