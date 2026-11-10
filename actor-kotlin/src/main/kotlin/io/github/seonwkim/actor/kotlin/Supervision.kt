package io.github.seonwkim.actor.kotlin

import io.github.seonwkim.actor.SupervisionStrategy
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** [SupervisionStrategy.Restart.withLimit] with a Kotlin [Duration]: `restart().withLimit(10, 1.minutes)`. */
fun SupervisionStrategy.Restart.withLimit(
    maxRetries: Int,
    within: Duration,
): SupervisionStrategy.Restart = withLimit(maxRetries, within.toJavaDuration())
