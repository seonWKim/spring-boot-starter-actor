package io.github.seonwkim.actor.autoconfigure.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.seonwkim.actor.delivery.StaleGenerationException;
import io.github.seonwkim.actor.generation.ActivationEvent;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.test.InMemoryGenerationStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fencing with the epoch, and the audit of every attempt. */
class StoreDeploymentGenerationTest {

    private final InMemoryGenerationStore store = new InMemoryGenerationStore();
    private final List<ActivationEvent> events = new ArrayList<>();

    private StoreDeploymentGeneration node(String color, Duration refresh, Clock clock) {
        return new StoreDeploymentGeneration(
                store, "orders", color, refresh, clock, event -> events.add((ActivationEvent) event));
    }

    private StoreDeploymentGeneration node(String color) {
        return node(color, Duration.ZERO, Clock.systemUTC());
    }

    @Test
    void workOfBlueStaysFencedAfterBlueGreenBlue() {
        StoreDeploymentGeneration blue = node("blue");
        StoreDeploymentGeneration green = node("green");
        blue.activate("blue", 0, "deploy-1");
        long blueWork = blue.stamp();

        green.activate("green", 1, "deploy-2");
        blue.activate("blue", 2, "deploy-3");

        assertThat(blue.isActive()).isTrue();
        assertThat(blue.stamp()).isEqualTo(3);
        assertThatThrownBy(() -> blue.check(blueWork)).isInstanceOf(StaleGenerationException.class);
        blue.check(blue.stamp());
    }

    @Test
    void aColorThatIsNotActiveCannotStampWork() {
        StoreDeploymentGeneration blue = node("blue");
        StoreDeploymentGeneration green = node("green");
        assertThatThrownBy(blue::stamp).isInstanceOf(StaleGenerationException.class);

        blue.activate("green", 0, "deploy-1");

        assertThat(blue.isActive()).isFalse();
        assertThat(green.isActive()).isTrue();
        assertThatThrownBy(blue::stamp).isInstanceOf(StaleGenerationException.class);
    }

    @Test
    void aReadIsReusedForTheRefreshIntervalButAnActivationHereReplacesItAtOnce() {
        MutableClock clock = new MutableClock();
        StoreDeploymentGeneration blue = node("blue", Duration.ofSeconds(1), clock);
        StoreDeploymentGeneration green = node("green", Duration.ofSeconds(1), clock);
        green.activate("blue", 0, "deploy-1");
        assertThat(blue.isActive()).isTrue();

        green.activate("green", 1, "deploy-2");

        assertThat(green.isActive()).isTrue();
        assertThat(blue.isActive()).as("blue's read is still fresh").isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(blue.isActive()).isFalse();
    }

    @Test
    void everyAttemptIsAuditedWithItsOutcome() {
        StoreDeploymentGeneration blue = node("blue");

        blue.activate("blue", 0, "deploy-1");
        blue.activate("blue", 0, "deploy-1");
        blue.activate("green", 0, "deploy-2");

        assertThat(events)
                .extracting(event -> event.result().outcome())
                .containsExactly(Outcome.ACTIVATED, Outcome.ALREADY_ACTIVATED, Outcome.EPOCH_MISMATCH);
        assertThat(events.get(2).operationId()).isEqualTo("deploy-2");
        assertThat(events.get(2).deployment()).isEqualTo("orders");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-11T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
