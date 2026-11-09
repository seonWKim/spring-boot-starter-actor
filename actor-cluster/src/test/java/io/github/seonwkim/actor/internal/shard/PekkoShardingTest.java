package io.github.seonwkim.actor.internal.shard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.shard.ShardEnvelope;
import io.github.seonwkim.actor.shard.ShardingStrategy;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.junit.jupiter.api.Test;

class PekkoShardingTest {

    @Test
    void hashedStrategySpreadsEntitiesOverExactlyItsNumberOfShards() {
        ShardingMessageExtractor<ShardEnvelope<String>, String> extractor =
                PekkoSharding.extractor(ShardingStrategy.hashed(5));

        Set<String> shards = IntStream.range(0, 1_000)
                .mapToObj(i -> extractor.shardId("entity-" + i))
                .collect(Collectors.toSet());

        assertThat(shards).containsExactlyInAnyOrder("0", "1", "2", "3", "4");
    }
}
