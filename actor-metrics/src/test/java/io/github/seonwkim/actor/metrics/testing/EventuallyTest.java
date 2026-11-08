package io.github.seonwkim.actor.metrics.testing;

import static io.github.seonwkim.actor.metrics.testing.Eventually.eventually;
import static io.github.seonwkim.actor.metrics.testing.Eventually.eventuallyStays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventuallyTest {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void eventuallyWaitsForAValueSetLater() {
        AtomicInteger counter = new AtomicInteger();
        executor.schedule(() -> counter.set(1), 100, TimeUnit.MILLISECONDS);

        eventually(() -> assertEquals(1, counter.get()));
    }

    @Test
    void eventuallyStaysAcceptsAValueThatKeepsHolding() {
        AtomicInteger counter = new AtomicInteger();
        executor.schedule(() -> counter.set(1), 100, TimeUnit.MILLISECONDS);

        eventuallyStays(() -> assertEquals(1, counter.get()));
    }

    @Test
    void eventuallyStaysRejectsACounterThatGoesPastTheExpectedValue() {
        AtomicInteger counter = new AtomicInteger();
        executor.schedule(() -> counter.set(1), 50, TimeUnit.MILLISECONDS);
        executor.schedule(() -> counter.set(2), 250, TimeUnit.MILLISECONDS);

        assertThrows(ConditionTimeoutException.class, () -> eventuallyStays(() -> assertEquals(1, counter.get())));
    }
}
