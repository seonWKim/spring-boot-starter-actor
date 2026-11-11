package io.github.seonwkim.actor.internal.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.internal.delivery.RememberedKeys.Seen;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RememberedKeysTest {

    @Test
    void pastTheLimitTheOldestKeyIsForgottenFirst() {
        RememberedKeys keys = new RememberedKeys(Duration.ofMinutes(10), 2, () -> 0L);
        keys.remember("a", 1);
        keys.remember("b", 1);
        keys.remember("c", 1);

        assertThat(keys.check("a", 1)).isEqualTo(Seen.NEW);
        assertThat(keys.check("b", 1)).isEqualTo(Seen.DUPLICATE);
        assertThat(keys.check("c", 1)).isEqualTo(Seen.DUPLICATE);
    }

    @Test
    void aKeyWithADifferentFingerprintConflictsUnlessEitherHasNone() {
        RememberedKeys keys = new RememberedKeys(Duration.ofMinutes(10), 10, () -> 0L);
        keys.remember("a", 1);
        keys.remember("b", null);

        assertThat(keys.check("a", 2)).isEqualTo(Seen.CONFLICT);
        assertThat(keys.check("a", null)).isEqualTo(Seen.DUPLICATE);
        assertThat(keys.check("b", 2)).isEqualTo(Seen.DUPLICATE);
    }
}
