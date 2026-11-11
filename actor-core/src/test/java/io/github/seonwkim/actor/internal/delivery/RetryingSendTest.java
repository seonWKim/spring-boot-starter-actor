package io.github.seonwkim.actor.internal.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryingSendTest {

    @Test
    void backoffDoublesUpToTheMaximumAndStaysThere() {
        Duration max = Duration.ofSeconds(5);
        Duration backoff = Duration.ofMillis(200);

        for (int attempt = 0; attempt < 100; attempt++) {
            Duration next = RetryingSend.nextBackoff(backoff, max);
            assertThat(next).isBetween(backoff, max);
            backoff = next;
        }
        assertThat(backoff).isEqualTo(max);
        assertThat(RetryingSend.nextBackoff(Duration.ofMillis(200), max)).isEqualTo(Duration.ofMillis(400));
    }
}
