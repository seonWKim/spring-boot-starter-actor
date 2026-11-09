package io.github.seonwkim.actor.internal.cluster.discovery;

import com.typesafe.config.Config;
import io.github.seonwkim.actor.internal.SpringBeansSetup;
import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.Done;
import org.apache.pekko.actor.Cancellable;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.discovery.Lookup;
import org.apache.pekko.discovery.ServiceDiscovery;
import org.apache.pekko.dispatch.Dispatchers;
import org.apache.pekko.management.PekkoManagementSettings;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.FutureConverters;

/**
 * Pekko discovery on a {@link PeerRegistry} bean ({@code spring.actor.cluster.join=discovery}).
 * Cluster Bootstrap asks it for contact points; it answers with the peers the registry lists.
 *
 * <p>It also keeps this node's own entry: it registers the node (its management host and port)
 * when Pekko creates it, renews the entry every {@code heartbeat-interval}, and removes it in the
 * first phase of coordinated shutdown, so a node that leaves gracefully is no longer offered as a
 * contact point. Registry calls may block, so they run on the blocking dispatcher.
 */
public final class PeerRegistryServiceDiscovery extends ServiceDiscovery {

    /** The discovery method name ({@code pekko.discovery.method}) this class is registered under. */
    public static final String METHOD = "spring-actor-peer-registry";

    private static final Logger log = LoggerFactory.getLogger(PeerRegistryServiceDiscovery.class);

    private final PeerRegistry registry;
    private final Duration ttl;
    private final ExecutionContextExecutor blocking;
    private final Peer self;

    public PeerRegistryServiceDiscovery(ExtendedActorSystem system) {
        Config config = system.settings().config().getConfig("pekko.discovery." + METHOD);
        this.ttl = config.getDuration("ttl");
        Duration heartbeatInterval = config.getDuration("heartbeat-interval");
        this.registry = system.settings()
                .setup()
                .get(SpringBeansSetup.class)
                .map(beans ->
                        beans.beanFactory().getBeanProvider(PeerRegistry.class).getIfUnique())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "spring.actor.cluster.join=discovery needs one PeerRegistry bean: set spring.actor.cluster.discovery.method"
                                        + " to jdbc (with actor-jdbc) or redis (with actor-redis), or define your own PeerRegistry bean"));
        PekkoManagementSettings management =
                new PekkoManagementSettings(system.settings().config());
        this.self = new Peer(
                ClusterBootstrap.get(system).settings().contactPointDiscovery().effectiveName(system),
                system.provider().getDefaultAddress().toString(),
                management.getHttpHostname(),
                management.getHttpPort());
        this.blocking = system.dispatchers().lookup(Dispatchers.DefaultBlockingDispatcherId());

        registry.register(self);
        log.info("Registered {} in {}", self, registry.getClass().getSimpleName());
        Cancellable heartbeat =
                system.scheduler().scheduleWithFixedDelay(heartbeatInterval, heartbeatInterval, this::beat, blocking);
        CoordinatedShutdown.get(system)
                .addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "deregister-peer", () -> {
                    heartbeat.cancel();
                    return CompletableFuture.supplyAsync(
                            () -> {
                                registry.deregister(self);
                                log.info("Deregistered {}", self);
                                return Done.getInstance();
                            },
                            blocking);
                });
    }

    private void beat() {
        try {
            registry.heartbeat(self);
        } catch (RuntimeException e) {
            // The next beat tries again; the entry expires only after the ttl.
            log.warn("Peer heartbeat for {} failed", self, e);
        }
    }

    @Override
    public Future<Resolved> lookup(Lookup lookup, FiniteDuration resolveTimeout) {
        CompletableFuture<Resolved> resolved = CompletableFuture.supplyAsync(
                        () -> resolve(lookup.serviceName(), registry.peers(lookup.serviceName(), ttl)), blocking)
                .orTimeout(resolveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        return FutureConverters.asScala(resolved);
    }

    private static Resolved resolve(String service, List<Peer> peers) {
        List<ResolvedTarget> targets = peers.stream()
                .map(peer -> new ResolvedTarget(peer.host(), Option.<Object>apply(peer.port()), Option.empty()))
                .toList();
        return new Resolved(service, CollectionConverters.asScala(targets).toList());
    }
}
