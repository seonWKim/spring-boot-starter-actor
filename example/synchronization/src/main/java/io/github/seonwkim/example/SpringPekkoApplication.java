package io.github.seonwkim.example;

import io.github.seonwkim.core.EnableActorSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "io.github.seonwkim.example.counter")
@EnableActorSupport
public class SpringPekkoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringPekkoApplication.class, args);
    }
}
