package io.github.seonwkim.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.seonwkim.core.EnableActorSupport;

@SpringBootApplication
@EnableActorSupport
public class SpringPekkoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringPekkoApplication.class, args);
	}
}
