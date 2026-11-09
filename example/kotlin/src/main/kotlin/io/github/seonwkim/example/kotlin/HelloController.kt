package io.github.seonwkim.example.kotlin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** HTTP endpoints for the hello actor. WebFlux runs the suspend functions as coroutines. */
@RestController
class HelloController(
    private val helloService: HelloService,
) {
    @GetMapping("/hello")
    suspend fun hello(): String = helloService.hello()

    @GetMapping("/hello/restart")
    suspend fun triggerRestart(): String = helloService.triggerRestart()

    @GetMapping("/hello/stop")
    suspend fun stopActor(): String = helloService.stopActor()
}
