package io.github.seonwkim.example.kotlin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KotlinExampleApplication

fun main(args: Array<String>) {
    runApplication<KotlinExampleApplication>(*args)
}
