package io.github.seonwkim.core;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.metrics.ClusterMetricsChanged;
import org.apache.pekko.cluster.metrics.ClusterMetricsEvent;
import org.apache.pekko.cluster.metrics.NodeMetrics;
import org.apache.pekko.cluster.metrics.StandardMetrics;
import org.apache.pekko.cluster.metrics.StandardMetrics.Cpu;
import org.apache.pekko.cluster.metrics.StandardMetrics.HeapMemory;
import org.apache.pekko.cluster.typed.Cluster;

public class PekkoClusterMetricsListener implements SpringActor {

    private final SpringActorSystem springActorSystem;
    private final Cluster cluster;

    public PekkoClusterMetricsListener(SpringActorSystem springActorSystem) {
        if (springActorSystem.getCluster() == null) {
            throw new IllegalStateException("Cluster mode should be turned on");
        }
        this.springActorSystem = springActorSystem;
        this.cluster = springActorSystem.getCluster();
    }

    @Override
    public Class<?> commandClass() {
        return ClusterMetricsChanged.class;
    }

    @Override
    public Behavior<ClusterMetricsEvent> create(String id) {
        return Behaviors.setup(
                ctx ->
                        Behaviors.receive(ClusterMetricsEvent.class)
                                 .onMessage(ClusterMetricsChanged.class, clusterMetrics -> {
                                     for (NodeMetrics nodeMetrics : clusterMetrics.getNodeMetrics()) {
                                         if (nodeMetrics.address().equals(cluster.selfMember().address())) {
                                             logHeap(nodeMetrics);
                                             logCpu(nodeMetrics);
                                         }
                                     }
                                     return Behaviors.same();
                                 })
                                 .build()
        );
    }

    void logHeap(NodeMetrics nodeMetrics) {
        HeapMemory heap = StandardMetrics.extractHeapMemory(nodeMetrics);
        if (heap != null) {
            springActorSystem.getLogger().info("Used heap: {} MB", ((double) heap.used()) / 1024 / 1024);
        }
    }

    void logCpu(NodeMetrics nodeMetrics) {
        Cpu cpu = StandardMetrics.extractCpu(nodeMetrics);
        if (cpu != null && cpu.systemLoadAverage().isDefined()) {
            springActorSystem.getLogger().info("Load: {} ({} processors)", cpu.systemLoadAverage().get(),
                                               cpu.processors());
        }
    }
}
