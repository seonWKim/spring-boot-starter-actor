package io.github.seonwkim.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.github.seonwkim.core.EnableActorSupport;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "io.github.seonwkim.example.counter")
@EnableActorSupport
public class SpringPekkoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringPekkoApplication.class, args);
	}
}
