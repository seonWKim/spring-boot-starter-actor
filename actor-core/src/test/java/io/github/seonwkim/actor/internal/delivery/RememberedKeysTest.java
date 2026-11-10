package io.github.seonwkim.actor.internal.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RememberedKeysTest {

    @Test
    void pastTheLimitTheOldestKeyIsForgottenFirst() {
        RememberedKeys keys = new RememberedKeys(Duration.ofMinutes(10), 2, () -> 0L);
        keys.remember("a");
        keys.remember("b");
        keys.remember("c");

        assertThat(keys.contains("a")).isFalse();
        assertThat(keys.contains("b")).isTrue();
        assertThat(keys.contains("c")).isTrue();
    }
}
