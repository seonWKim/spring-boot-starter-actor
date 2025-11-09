package io.github.seonwkim.example.persistence;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableActorSupport
public class PersistenceExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersistenceExampleApplication.class, args);
    }
}
