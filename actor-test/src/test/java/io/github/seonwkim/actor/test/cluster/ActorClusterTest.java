package io.github.seonwkim.actor.test.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.test.ActorCluster;
import io.github.seonwkim.actor.test.ActorClusterExtension;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ActorClusterTest {

    @RegisterExtension
    final ActorClusterExtension extension = new ActorClusterExtension(
            ActorCluster.builder(ClusterApplication.class).nodes(3));

    @Test
    void threeNodesFormOneCluster() {
        ActorCluster cluster = extension.cluster();

        assertThat(IntStream.rangeClosed(1, 3).map(cluster::upMembers)).containsOnly(3);
        assertThat(IntStream.rangeClosed(1, 3)
                        .mapToObj(n -> cluster.node(n).getBean(SpringActorSystem.class))
                        .map(SpringActorSystem::nodeAddress))
                .doesNotHaveDuplicates();
    }

    @Test
    void theOtherNodesSeeAStoppedNodeLeave() {
        ActorCluster cluster = extension.cluster();

        cluster.stop(3);

        cluster.awaitMembersUp(2);
        assertThat(cluster.upMembers(1)).isEqualTo(2);
        assertThat(cluster.upMembers(2)).isEqualTo(2);
    }
}
