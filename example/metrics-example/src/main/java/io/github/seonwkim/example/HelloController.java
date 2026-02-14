package io.github.seonwkim.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping("/debug/actor-path")
    public Mono<String> actorPath() {
        return helloService.getActorPath();
    }

    @GetMapping("/hello")
    public Mono<String> hello() {
        return helloService.hello();
    }

    @GetMapping("/hello/restart")
    public Mono<String> triggerRestart() {
        return helloService.triggerRestart();
    }

    @GetMapping("/hello/stop")
    public Mono<String> stopActor() {
        return helloService.stopActor();
    }
}
