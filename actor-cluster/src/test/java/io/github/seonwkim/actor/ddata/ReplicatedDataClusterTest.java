package io.github.seonwkim.actor.ddata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.AbstractClusterTest;
import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/** Replicated maps, counters and sets written on one node are read on another. */
class ReplicatedDataClusterTest extends AbstractClusterTest {

    private static final Consistency MAJORITY = Consistency.majority(Duration.ofSeconds(5));

    /** A node. Not a {@code @Configuration}, so the component scans of other tests skip it. */
    @EnableAutoConfiguration
    @EnableActorSupport
    static class Node {}

    /** A value that crosses nodes through the starter's Jackson binding. */
    record Cart(List<String> items, int quantity) implements JsonSerializable {}

    @Override
    protected Class<?> getApplicationClass() {
        return Node.class;
    }

    private static ReplicatedData data(ConfigurableApplicationContext node) {
        return node.getBean(ReplicatedData.class);
    }

    @Test
    void mapValueWrittenOnOneNodeIsReadOnAnother() {
        waitUntilClusterInitialized();
        ReplicatedMap<String, Cart> onNode1 = data(context1).map("carts");
        ReplicatedMap<String, Cart> onNode2 = data(context2).map("carts");
        Consistency all = Consistency.all(Duration.ofSeconds(5));

        // A local write is on node 1 only; a read from all nodes on node 2 asks node 1 for it.
        onNode1.put("alice", new Cart(List.of("book", "pen"), 2))
                .toCompletableFuture()
                .join();
        assertThat(onNode2.get("alice", all).toCompletableFuture().join())
                .contains(new Cart(List.of("book", "pen"), 2));

        // A write to all nodes is on node 2 when it completes; a local read there finds it.
        onNode1.put("bob", new Cart(List.of("cup"), 1), all)
                .toCompletableFuture()
                .join();
        assertThat(onNode2.get("bob").toCompletableFuture().join()).contains(new Cart(List.of("cup"), 1));
        assertThat(onNode2.get("carol").toCompletableFuture().join()).isEqualTo(Optional.empty());
    }

    @Test
    void counterChangesFromEveryNodeAddUp() {
        waitUntilClusterInitialized();

        data(context1).counter("visits").increment(3).toCompletableFuture().join();
        data(context2).counter("visits").increment(4).toCompletableFuture().join();
        data(context3).counter("visits").decrement(2).toCompletableFuture().join();

        ReplicatedCounter onNode1 = data(context1).counter("visits");
        await().atMost(Duration.ofSeconds(10))
                .until(() -> onNode1.value().toCompletableFuture().join() == 5L);
    }

    @Test
    void setAddsAndRemovesFromDifferentNodesMerge() {
        waitUntilClusterInitialized();
        ReplicatedSet<String> onNode1 = data(context1).set("online");
        ReplicatedSet<String> onNode2 = data(context2).set("online");

        onNode1.add("alice", MAJORITY).toCompletableFuture().join();
        onNode2.add("bob", MAJORITY).toCompletableFuture().join();
        onNode1.remove("alice", MAJORITY).toCompletableFuture().join();

        assertThat(data(context3)
                        .<String>set("online")
                        .elements(MAJORITY)
                        .toCompletableFuture()
                        .join())
                .isEqualTo(Set.of("bob"));
    }
}
