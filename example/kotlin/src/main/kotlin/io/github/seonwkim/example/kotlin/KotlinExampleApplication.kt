package io.github.seonwkim.example.kotlin

import io.github.seonwkim.actor.EnableActorSupport
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableActorSupport
class KotlinExampleApplication

fun main(args: Array<String>) {
    runApplication<KotlinExampleApplication>(*args)
}
