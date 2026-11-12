package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typesafe.config.Config;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.delivery.ReliableDeliveryException;
import io.github.seonwkim.actor.delivery.SpringReliableDelivery;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.delivery.ReliableConsumerModule;
import java.beans.PropertyDescriptor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
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
    void deliverySettingsReachTheReliableConsumers() {
        runner.withPropertyValues(
                        "spring.actor.delivery.idempotency-ttl=90s", "spring.actor.delivery.max-remembered-keys=500")
                .run(context -> {
                    Config config = pekkoConfig(context);
                    assertThat(config.getDuration(ReliableConsumerModule.TTL_KEY))
                            .isEqualTo(Duration.ofSeconds(90));
                    assertThat(config.getInt(ReliableConsumerModule.MAX_KEYS_KEY))
                            .isEqualTo(500);
                });
    }

    @Test
    void retrySettingsReachTheReliableSenders() {
        runner.withPropertyValues(
                        "spring.actor.delivery.retry.max-attempts=2",
                        "spring.actor.delivery.retry.attempt-timeout=100ms",
                        "spring.actor.delivery.retry.initial-backoff=10ms")
                .run(context -> {
                    // Never acks, so each attempt times out.
                    ActorSystem<?> system =
                            context.getBean(SpringActorSystem.class).unwrap(ActorSystem.class);
                    ActorRef<Object> silent = system.systemActorOf(Behaviors.ignore(), "never-acks", Props.empty());
                    CompletableFuture<Void> send = context.getBean(SpringReliableDelivery.class)
                            .to(new PekkoActorRef<>(silent))
                            .build()
                            .send("hello")
                            .toCompletableFuture();

                    assertThatThrownBy(() -> send.get(10, TimeUnit.SECONDS))
                            .cause()
                            .isInstanceOfSatisfying(ReliableDeliveryException.class, e -> assertThat(e.attempts())
                                    .isEqualTo(2));
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

    @ParameterizedTest
    @ValueSource(strings = {"spring.actor.pekko.cluster.seed-nodes", "spring.actor.pekko.cluster.seed-nodes[0]"})
    void aRawSingleValueForAListKeyIsAOneItemList(String key) {
        runner.withPropertyValues(key + "=pekko://orders@10.0.0.7:25521")
                .run(context -> assertThat(pekkoConfig(context).getStringList("pekko.cluster.seed-nodes"))
                        .containsExactly("pekko://orders@10.0.0.7:25521"));
    }

    @Test
    void metricsSettingsStayOutOfThePekkoConfig() {
        Map<String, Object> config =
                new ActorProperties().toPekkoConfig(Map.of("metrics", Map.of("path-tag-limit", "3")), false);

        assertThat(config).doesNotContainKey("metrics");
    }

    @Test
    void generationSettingsStayOutOfThePekkoConfig() {
        runner.withPropertyValues(
                        "spring.actor.generation.color=blue",
                        "spring.actor.generation.store=custom",
                        "spring.actor.generation.refresh=2s")
                .run(context ->
                        assertThat(pekkoConfig(context).hasPath("generation")).isFalse());
    }

    @Test
    void everyTypedSettingIsATypedRoot() {
        List<String> roots = Arrays.stream(BeanUtils.getPropertyDescriptors(ActorProperties.class))
                .map(PropertyDescriptor::getName)
                .filter(name -> !name.equals("class"))
                .map(name -> name.replaceAll("([A-Z])", "-$1").toLowerCase(Locale.ROOT))
                .toList();

        assertThat(ActorProperties.TYPED_ROOTS).containsAll(roots);
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
        runner.withPropertyValues(
                        "spring.actor.cluster.join=discovery",
                        "spring.actor.cluster.discovery.method=jdbc",
                        "spring.actor.cluster.discovery.heartbeat-interval=2s",
                        "spring.actor.cluster.discovery.ttl=12s")
                .run(context -> {
                    Config config = pekkoConfig(context);
                    assertThat(config.getString("pekko.discovery.method")).isEqualTo("spring-actor-peer-registry");
                    assertThat(config.getDuration("pekko.discovery.spring-actor-peer-registry.heartbeat-interval"))
                            .isEqualTo(Duration.ofSeconds(2));
                    assertThat(config.getDuration("pekko.discovery.spring-actor-peer-registry.ttl"))
                            .isEqualTo(Duration.ofSeconds(12));
                    // The registry lists only management ports: Bootstrap must keep them all.
                    assertThat(config.getBoolean(
                                    "pekko.management.cluster.bootstrap.contact-point.filter-on-fallback-port"))
                            .isFalse();
                });
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

    @Test
    void discoveryDownsUnreachableNodesWithKeepMajorityByDefault() {
        runner.withPropertyValues("spring.actor.cluster.join=discovery", "spring.actor.cluster.discovery.method=dns")
                .run(context -> {
                    Config config = pekkoConfig(context);
                    assertThat(config.getString("pekko.cluster.downing-provider-class"))
                            .isEqualTo("org.apache.pekko.cluster.sbr.SplitBrainResolverProvider");
                    assertThat(config.getString("pekko.cluster.split-brain-resolver.active-strategy"))
                            .isEqualTo("keep-majority");
                });
    }

    @Test
    void leaseMajorityAndUseLeasePointPekkoAtTheLeaseStoreWithItsTimings() {
        runner.withPropertyValues(
                        "spring.actor.cluster.downing.strategy=lease-majority",
                        "spring.actor.cluster.singleton.use-lease=true",
                        "spring.actor.cluster.sharding.use-lease=true",
                        "spring.actor.cluster.lease.store=custom",
                        "spring.actor.cluster.lease.ttl=60s",
                        "spring.actor.cluster.lease.renew-interval=6s",
                        "spring.actor.cluster.lease.expiry-margin=5s")
                .run(context -> {
                    Config config = pekkoConfig(context);
                    assertThat(config.getString("pekko.cluster.split-brain-resolver.active-strategy"))
                            .isEqualTo("lease-majority");
                    assertThat(config.getString(
                                    "pekko.cluster.split-brain-resolver.lease-majority.lease-implementation"))
                            .isEqualTo("spring-actor.lease");
                    assertThat(config.getString("pekko.cluster.singleton.use-lease"))
                            .isEqualTo("spring-actor.lease");
                    assertThat(config.getString("pekko.cluster.sharding.use-lease"))
                            .isEqualTo("spring-actor.lease");
                    assertThat(config.getDuration("spring-actor.lease.heartbeat-timeout"))
                            .isEqualTo(Duration.ofSeconds(60));
                    assertThat(config.getDuration("spring-actor.lease.heartbeat-interval"))
                            .isEqualTo(Duration.ofSeconds(6));
                    assertThat(config.getDuration("spring-actor.lease.expiry-margin"))
                            .isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void aLeaseUserWithoutAStoreFailsNamingTheKeys() {
        runner.withPropertyValues("spring.actor.cluster.singleton.use-lease=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining(
                                "spring.actor.cluster.singleton.use-lease needs spring.actor.cluster.lease.store"));
    }

    @Test
    void staticQuorumNeedsAQuorumSize() {
        runner.withPropertyValues("spring.actor.cluster.downing.strategy=static-quorum")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasStackTraceContaining("needs spring.actor.cluster.downing.quorum-size"));
    }

    @Test
    void aClusterWithoutDowningIsWarnedAbout(CapturedOutput output) {
        ActorProperties properties = new ActorProperties();
        properties.setProvider(ActorProperties.Provider.CLUSTER);
        properties.toPekkoConfig(Map.of(), false);
        assertThat(output).contains("This cluster has no downing");

        properties.getCluster().getDowning().setStrategy(ActorProperties.Downing.Strategy.KEEP_OLDEST);
        int warnings = output.toString().split("This cluster has no downing", -1).length;
        properties.toPekkoConfig(Map.of(), false);
        assertThat(output.toString().split("This cluster has no downing", -1)).hasSize(warnings);
    }
}
