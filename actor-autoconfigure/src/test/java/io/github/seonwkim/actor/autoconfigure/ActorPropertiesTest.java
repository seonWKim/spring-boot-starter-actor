package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typesafe.config.Config;
import io.github.seonwkim.actor.SpringActorSystem;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Typed spring.actor.* settings reach the actor system's Pekko configuration. */
@ExtendWith(OutputCaptureExtension.class)
class ActorPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(UserConfigurations.of(ActorConfiguration.class));

    private static Config pekkoConfig(org.springframework.context.ApplicationContext context) {
        return context.getBean(SpringActorSystem.class)
                .unwrap(ActorSystem.class)
                .settings()
                .config();
    }

    @Test
    void typedSettingsSetTheirPekkoKeys() {
        runner.withPropertyValues(
                        "spring.actor.name=orders",
                        "spring.actor.remote.hostname=10.0.0.7",
                        "spring.actor.remote.port=25521",
                        "spring.actor.cluster.seed-nodes=pekko://orders@10.0.0.7:25521,pekko://orders@10.0.0.8:25521",
                        "spring.actor.cluster.roles=api,worker")
                .run(context -> {
                    Config config = pekkoConfig(context);
                    assertThat(context.getBean(SpringActorSystem.class)
                                    .unwrap(ActorSystem.class)
                                    .name())
                            .isEqualTo("orders");
                    assertThat(config.getString("pekko.remote.artery.canonical.hostname"))
                            .isEqualTo("10.0.0.7");
                    assertThat(config.getInt("pekko.remote.artery.canonical.port"))
                            .isEqualTo(25521);
                    assertThat(config.getStringList("pekko.cluster.seed-nodes"))
                            .containsExactly("pekko://orders@10.0.0.7:25521", "pekko://orders@10.0.0.8:25521");
                    assertThat(config.getStringList("pekko.cluster.roles")).containsExactly("api", "worker");
                });
    }

    @Test
    void providerSetsPekkoActorProvider() {
        ActorProperties properties = new ActorProperties();
        properties.setProvider(ActorProperties.Provider.CLUSTER);

        assertThat(properties.toPekkoConfig(Map.of(), false))
                .isEqualTo(Map.of("pekko", Map.of("actor", Map.of("provider", "cluster"))));
    }

    @Test
    void aRawPekkoKeyWinsOverTheTypedSettingAndSaysSo(CapturedOutput output) {
        runner.withPropertyValues("spring.actor.name=typed", "spring.actor.pekko.name=raw")
                .run(context -> {
                    assertThat(pekkoConfig(context).getString("pekko.name")).isEqualTo("raw");
                    assertThat(output).contains("spring.actor.pekko.name=raw overrides spring.actor.name=typed");
                });
    }

    @Test
    void aRawKeyWithTheSameValueIsNotReported(CapturedOutput output) {
        runner.withPropertyValues("spring.actor.cluster.roles=api", "spring.actor.pekko.cluster.roles=api")
                .run(context -> {
                    assertThat(pekkoConfig(context).getStringList("pekko.cluster.roles"))
                            .isEqualTo(List.of("api"));
                    assertThat(output).doesNotContain("overrides spring.actor.cluster.roles");
                });
    }

    @Test
    void anInvalidValueFailsStartupNamingTheKey() {
        runner.withPropertyValues("spring.actor.remote.port=70000")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("spring.actor.remote.port must be between 0 and 65535, got 70000"));
    }

    @Test
    void seedNodesMustBePekkoAddresses() {
        runner.withPropertyValues("spring.actor.cluster.seed-nodes=10.0.0.7:25521")
                .run(
                        context -> assertThat(context.getStartupFailure())
                                .hasStackTraceContaining(
                                        "spring.actor.cluster.seed-nodes are addresses like pekko://<name>@<host>:<port>, got '10.0.0.7:25521'"));
    }

    @Test
    void discoveryThroughAStoreUsesThePeerRegistryAdapterWithItsTimings() {
        ActorProperties properties = new ActorProperties();
        properties.getCluster().setJoin(ActorProperties.Cluster.Join.DISCOVERY);
        properties.getCluster().getDiscovery().setMethod(ActorProperties.Discovery.Method.JDBC);
        properties.getCluster().getDiscovery().setHeartbeatInterval(Duration.ofSeconds(2));
        properties.getCluster().getDiscovery().setTtl(Duration.ofSeconds(12));

        assertThat(properties.toPekkoConfig(Map.of(), false))
                .isEqualTo(Map.of(
                        "pekko",
                        Map.of(
                                "discovery",
                                Map.of(
                                        "method",
                                        "spring-actor-peer-registry",
                                        "spring-actor-peer-registry",
                                        Map.of("heartbeat-interval", "2000ms", "ttl", "12000ms")),
                                "management",
                                Map.of(
                                        "cluster",
                                        Map.of(
                                                "bootstrap",
                                                Map.of("contact-point", Map.of("filter-on-fallback-port", false)))))));
    }

    @Test
    void discoveryThroughDnsUsesPekkosMethodName() {
        runner.withPropertyValues("spring.actor.cluster.join=discovery", "spring.actor.cluster.discovery.method=dns")
                .run(context -> assertThat(pekkoConfig(context).getString("pekko.discovery.method"))
                        .isEqualTo("pekko-dns"));
    }

    @Test
    void discoveryWithoutAMethodFailsNamingTheKey() {
        runner.withPropertyValues("spring.actor.cluster.join=discovery")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "spring.actor.cluster.join=discovery needs spring.actor.cluster.discovery.method"));
    }

    @Test
    void discoveryRejectsSeedNodes() {
        runner.withPropertyValues(
                        "spring.actor.cluster.join=discovery",
                        "spring.actor.cluster.discovery.method=jdbc",
                        "spring.actor.cluster.seed-nodes=pekko://orders@10.0.0.7:25521")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("remove spring.actor.cluster.seed-nodes and join-self"));
    }
}
