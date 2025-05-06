package org.github.seonwkim.example;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class HelloActor {

    public Mono<String> hello() {
        return Mono.just("hello");
    }
}
