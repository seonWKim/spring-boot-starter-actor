package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** spring.actor.cluster.join-self forms a one-node cluster on any port, without seed nodes. */
class JoinSelfTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    private static ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.pekko.management.enabled=false")
                .properties(properties)
                .run();
    }

    private static MemberStatus selfStatus(ConfigurableApplicationContext context) {
        return context.getBean(SpringActorSystem.class)
                .unwrap(Cluster.class)
                .selfMember()
                .status();
    }

    @Test
    void joinSelfMakesThisNodeUp() {
        try (ConfigurableApplicationContext context = start("spring.actor.cluster.join-self=true")) {
            await().atMost(Duration.ofSeconds(20)).until(() -> selfStatus(context) == MemberStatus.up());
        }
    }

    @Test
    void joinSelfAndSeedNodesTogetherFailStartup() {
        Throwable failure = catchThrowable(() -> start(
                        "spring.actor.cluster.join-self=true",
                        "spring.actor.cluster.seed-nodes=pekko://system@127.0.0.1:2551")
                .close());

        assertThat(failure)
                .rootCause()
                .hasMessage("spring.actor.cluster.join-self and spring.actor.cluster.seed-nodes cannot both be set");
    }
}
