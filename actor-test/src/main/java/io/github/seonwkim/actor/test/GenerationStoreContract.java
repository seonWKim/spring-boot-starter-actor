package io.github.seonwkim.actor.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.generation.ActivationResult;
import io.github.seonwkim.actor.generation.ActivationResult.Outcome;
import io.github.seonwkim.actor.generation.ActiveGeneration;
import io.github.seonwkim.actor.spi.GenerationStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The behavior every {@link GenerationStore} must have. A store's test extends this class and returns
 * its store; each test uses its own deployment name, so the store may be shared between tests.
 */
public abstract class GenerationStoreContract {

    /** The store under test. Called once per test. */
    protected abstract GenerationStore store();

    private final String deployment = "deployment-" + UUID.randomUUID();

    @Test
    void ofTwentyConcurrentActivationsFromTheSameEpochExactlyOneWins() throws Exception {
        GenerationStore store = store();
        store.activate(deployment, "blue", 0, "op-blue");
        int deploys = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(deploys);
        try {
            List<Future<ActivationResult>> results = new ArrayList<>();
            for (int i = 0; i < deploys; i++) {
                String operation = "op-" + i;
                Callable<ActivationResult> activate = () -> {
                    start.await();
                    return store.activate(deployment, "green", 1, operation);
                };
                results.add(pool.submit(activate));
            }
            start.countDown();
            List<ActivationResult> winners = new ArrayList<>();
            for (Future<ActivationResult> result : results) {
                ActivationResult activation = result.get();
                if (activation.outcome() == Outcome.ACTIVATED) {
                    winners.add(activation);
                } else {
                    assertThat(activation.outcome()).isEqualTo(Outcome.EPOCH_MISMATCH);
                }
            }

            assertThat(winners).hasSize(1);
            assertThat(store.active(deployment)).contains(winners.get(0).generation());
            assertThat(Objects.requireNonNull(winners.get(0).generation()).epoch())
                    .isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theFirstActivationExpectsEpochZeroAndGetsEpochOne() {
        GenerationStore store = store();
        assertThat(store.active(deployment)).isEmpty();

        ActivationResult result = store.activate(deployment, "blue", 0, "op-1");

        assertThat(result.outcome()).isEqualTo(Outcome.ACTIVATED);
        ActiveGeneration active = store.active(deployment).orElseThrow();
        assertThat(active.color()).isEqualTo("blue");
        assertThat(active.epoch()).isEqualTo(1);
        assertThat(active.operationId()).isEqualTo("op-1");
        assertThat(active.activatedAt()).isNotNull();
        assertThat(result.generation()).isEqualTo(active);
    }

    @Test
    void aRetriedOperationReturnsTheGenerationItActivatedEvenAfterALaterActivation() {
        GenerationStore store = store();
        ActiveGeneration first = store.activate(deployment, "blue", 0, "op-1").generation();

        assertThat(store.activate(deployment, "blue", 0, "op-1"))
                .isEqualTo(new ActivationResult(Outcome.ALREADY_ACTIVATED, first));
        store.activate(deployment, "green", 1, "op-2");
        assertThat(store.activate(deployment, "blue", 0, "op-1"))
                .isEqualTo(new ActivationResult(Outcome.ALREADY_ACTIVATED, first));
        assertThat(store.active(deployment).orElseThrow().color()).isEqualTo("green");
    }

    @Test
    void anOperationIdReusedForAnotherColorOrEpochIsAConflict() {
        GenerationStore store = store();
        ActiveGeneration first = store.activate(deployment, "blue", 0, "op-1").generation();

        assertThat(store.activate(deployment, "green", 0, "op-1"))
                .isEqualTo(new ActivationResult(Outcome.OPERATION_CONFLICT, first));
        assertThat(store.activate(deployment, "blue", 1, "op-1"))
                .isEqualTo(new ActivationResult(Outcome.OPERATION_CONFLICT, first));
        assertThat(store.active(deployment)).contains(first);
    }

    @Test
    void aWrongExpectedEpochIsRejectedAndChangesNothing() {
        GenerationStore store = store();
        ActiveGeneration blue = store.activate(deployment, "blue", 0, "op-1").generation();

        assertThat(store.activate(deployment, "green", 0, "op-2"))
                .isEqualTo(new ActivationResult(Outcome.EPOCH_MISMATCH, blue));
        assertThat(store.activate(deployment, "green", 5, "op-3"))
                .isEqualTo(new ActivationResult(Outcome.EPOCH_MISMATCH, blue));
        assertThat(store.active(deployment)).contains(blue);
        // A rejected operation ID is not remembered: it may be retried with the right epoch.
        assertThat(store.activate(deployment, "green", 1, "op-2").outcome()).isEqualTo(Outcome.ACTIVATED);
    }

    @Test
    void theEpochKeepsGrowingWhenAColorIsActivatedAgain() {
        GenerationStore store = store();
        store.activate(deployment, "blue", 0, "op-1");
        store.activate(deployment, "green", 1, "op-2");

        ActivationResult blueAgain = store.activate(deployment, "blue", 2, "op-3");

        assertThat(blueAgain.outcome()).isEqualTo(Outcome.ACTIVATED);
        assertThat(Objects.requireNonNull(blueAgain.generation()).epoch()).isEqualTo(3);
    }

    @Test
    void deploymentsAreIndependent() {
        GenerationStore store = store();
        String other = deployment + "-other";
        store.activate(deployment, "blue", 0, "op-1");

        assertThat(store.active(other)).isEmpty();
        assertThat(store.activate(other, "green", 0, "op-1").outcome()).isEqualTo(Outcome.ACTIVATED);
        assertThat(store.active(deployment).orElseThrow().color()).isEqualTo("blue");
    }
}
