package org.github.seonwkim.example;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final HelloActor helloActor;

    public HelloService(HelloActor helloActor) {
        this.helloActor = helloActor;
    }

    public Mono<String> hello() {
        return helloActor.hello();
    }
}
