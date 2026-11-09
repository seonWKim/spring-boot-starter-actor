package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.internal.cluster.discovery.PeerRegistryServiceDiscovery;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.discovery.Discovery;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.javadsl.PekkoManagement;

/** Starts the Pekko Management HTTP server and Cluster Bootstrap. Needs both on the classpath. */
public final class PekkoManagementStarter {

    private PekkoManagementStarter() {}

    public static void startManagement(SpringActorSystem system) {
        PekkoManagement.get(PekkoActorSystem.raw(system).classicSystem()).start();
    }

    public static void startBootstrap(SpringActorSystem system) {
        ActorSystem classic = PekkoActorSystem.raw(system).classicSystem();
        if (PeerRegistryServiceDiscovery.METHOD.equals(
                classic.settings().config().getString("pekko.discovery.method"))) {
            // Register this node now, so a missing or broken PeerRegistry fails the startup
            // instead of failing every lookup. Bootstrap then reuses this instance.
            Discovery.get(classic).discovery();
        }
        ClusterBootstrap.get(classic).start();
    }
}
