package org.github.seonwkim.example.controller;

import org.github.seonwkim.example.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

	private final GreetingService greetingService;

	public GreetingController(GreetingService greetingService) {
		this.greetingService = greetingService;
	}

	@GetMapping("/api/v1/hello")
	public void hello() {
		greetingService.hello();
	}
}
