package io.github.seonwkim.actor.internal.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.cluster.MemberStatus;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PekkoClusterEventsTest {

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(org.apache.pekko.cluster.MemberStatus.joining(), MemberStatus.JOINING),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.weaklyUp(), MemberStatus.WEAKLY_UP),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.up(), MemberStatus.UP),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.leaving(), MemberStatus.LEAVING),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.exiting(), MemberStatus.EXITING),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.down(), MemberStatus.DOWN),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.removed(), MemberStatus.REMOVED),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.shuttingDown(), MemberStatus.PREPARING_FOR_SHUTDOWN),
                Arguments.of(org.apache.pekko.cluster.MemberStatus.shutDown(), MemberStatus.READY_FOR_SHUTDOWN));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void everyPekkoMemberStatusMapsToItsStarterStatus(
            org.apache.pekko.cluster.MemberStatus pekko, MemberStatus expected) {
        assertThat(PekkoClusterEvents.status(pekko)).isEqualTo(expected);
    }
}
