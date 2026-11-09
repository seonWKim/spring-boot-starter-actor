package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.javadsl.PekkoManagement;

/** Starts the Pekko Management HTTP server and Cluster Bootstrap. Needs both on the classpath. */
public final class PekkoManagementStarter {

    private PekkoManagementStarter() {}

    public static void startManagement(SpringActorSystem system) {
        PekkoManagement.get(PekkoActorSystem.raw(system).classicSystem()).start();
    }

    public static void startBootstrap(SpringActorSystem system) {
        ClusterBootstrap.get(PekkoActorSystem.raw(system).classicSystem()).start();
    }
}
