package io.github.seonwkim.actor.test;

import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.cluster.MemberStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * A cluster of Spring application contexts in this JVM, one per node, on free ports. Node 1 is the
 * only seed node, so it forms the cluster at once and the others join it.
 *
 * <pre>{@code
 * try (ActorCluster cluster = ActorCluster.builder(MyApp.class).nodes(3).start()) {
 *     SpringActorSystem second = cluster.node(2).getBean(SpringActorSystem.class);
 * }
 * }</pre>
 *
 * <p>In JUnit 5, {@link ActorClusterExtension} starts and stops it around each test or class.
 */
public final class ActorCluster implements AutoCloseable {

    private final List<Node> nodes;
    private final Duration timeout;

    private ActorCluster(List<Node> nodes, Duration timeout) {
        this.nodes = nodes;
        this.timeout = timeout;
    }

    /** A cluster whose nodes run the given Spring sources (usually the application class). */
    public static Builder builder(Class<?>... sources) {
        return new Builder(sources);
    }

    /** The context of a node, numbered from 1. */
    public ConfigurableApplicationContext node(int number) {
        if (number < 1 || number > nodes.size()) {
            throw new IllegalArgumentException("Node " + number + " is not in 1.." + nodes.size());
        }
        return nodes.get(number - 1).context;
    }

    /** The number of nodes started, running or stopped. */
    public int size() {
        return nodes.size();
    }

    /** Stops a node, numbered from 1. The others stay up; {@link #awaitMembersUp(int)} waits for them to see it gone. */
    public void stop(int number) {
        node(number).close();
    }

    /** The number of members a node sees as up. */
    public int upMembers(int number) {
        node(number);
        return nodes.get(number - 1).members.upCount();
    }

    /** Waits until every running node sees every running node up. */
    public void awaitUp() {
        awaitMembersUp((int) nodes.stream().filter(Node::running).count());
    }

    /** Waits until every running node sees exactly {@code count} members up. */
    public void awaitMembersUp(int count) {
        await("every running node sees " + count + " members up")
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> nodes.stream().filter(Node::running).allMatch(node -> node.members.upCount() == count));
    }

    /** Stops every node that is still running. */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (Node node : nodes) {
            try {
                if (node.running()) {
                    node.context.close();
                }
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private record Node(ConfigurableApplicationContext context, Members members) {
        boolean running() {
            return context.isActive();
        }
    }

    /** The members one node sees, kept from its cluster events (the first ones replay the current state). */
    private static final class Members implements ApplicationListener<ApplicationEvent> {

        private final Map<String, MemberStatus> statuses = new ConcurrentHashMap<>();

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof ClusterEvent e) {
                switch (e) {
                    case ClusterEvent.MemberJoined m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberWeaklyUp m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberUp m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberLeft m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberExited m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberDowned m -> statuses.put(
                            m.member().address(), m.member().status());
                    case ClusterEvent.MemberRemoved m -> statuses.remove(
                            m.member().address());
                    default -> {}
                }
            }
        }

        int upCount() {
            return (int) statuses.values().stream()
                    .filter(status -> status == MemberStatus.UP)
                    .count();
        }
    }

    /** Builds an {@link ActorCluster}. */
    public static final class Builder {

        private final Class<?>[] sources;
        private int nodes = 3;
        private String systemName = "actor-test-cluster";
        private Duration timeout = Duration.ofSeconds(30);
        private final List<String> properties = new ArrayList<>();
        private final Map<Integer, List<String>> nodeProperties = new HashMap<>();
        private final Map<Integer, ClassLoader> classLoaders = new HashMap<>();

        private Builder(Class<?>[] sources) {
            if (sources.length == 0) {
                throw new IllegalArgumentException("Give at least one Spring source, usually the application class");
            }
            this.sources = sources.clone();
        }

        /** The number of nodes; 3 by default. */
        public Builder nodes(int nodes) {
            if (nodes < 1) {
                throw new IllegalArgumentException("A cluster needs at least one node, got " + nodes);
            }
            this.nodes = nodes;
            return this;
        }

        /** The actor system name, the same on every node. */
        public Builder systemName(String systemName) {
            this.systemName = systemName;
            return this;
        }

        /** How long {@link #start()} and the waits wait for members to be up; 30 seconds by default. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Properties in {@code key=value} form for every node. They override the cluster's own. */
        public Builder properties(String... properties) {
            this.properties.addAll(List.of(properties));
            return this;
        }

        /** Properties in {@code key=value} form for one node, numbered from 1. */
        public Builder nodeProperties(int node, String... properties) {
            nodeProperties.computeIfAbsent(node, n -> new ArrayList<>()).addAll(List.of(properties));
            return this;
        }

        /** The cluster roles of one node, numbered from 1. */
        public Builder roles(int node, String... roles) {
            for (int i = 0; i < roles.length; i++) {
                nodeProperties(node, "spring.actor.pekko.cluster.roles[" + i + "]=" + roles[i]);
            }
            return this;
        }

        /**
         * Runs one node, numbered from 1, with its own class loader: its Spring context and actor system
         * load the application's classes through it. A loader that defines some classes itself, such
         * as the next version of a message class, runs two versions of the application in one JVM.
         */
        public Builder classLoader(int node, ClassLoader classLoader) {
            classLoaders.put(node, classLoader);
            return this;
        }

        /** Starts the nodes in order and waits until every node sees all of them up. */
        public ActorCluster start() {
            int[] ports = new int[nodes];
            for (int i = 0; i < nodes; i++) {
                ports[i] = freePort();
            }
            String seed = "pekko://" + systemName + "@127.0.0.1:" + ports[0];
            List<Node> started = new ArrayList<>();
            ActorCluster cluster = new ActorCluster(started, timeout);
            try {
                for (int i = 0; i < nodes; i++) {
                    started.add(startNode(i + 1, ports[i], seed));
                }
                cluster.awaitMembersUp(nodes);
            } catch (RuntimeException | Error e) {
                try {
                    cluster.close();
                } catch (RuntimeException closing) {
                    e.addSuppressed(closing);
                }
                throw e;
            }
            return cluster;
        }

        private Node startNode(int number, int port, String seed) {
            List<String> args = new ArrayList<>(List.of(
                    "spring.actor.pekko.name=" + systemName,
                    "spring.actor.pekko.actor.provider=cluster",
                    "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                    "spring.actor.pekko.remote.artery.canonical.port=" + port,
                    // One seed: the first node. As the first seed it forms the cluster at once; with
                    // every node as a seed, it first waits seed-node-timeout for the others.
                    "spring.actor.pekko.cluster.seed-nodes[0]=" + seed,
                    "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
                    // Used only when Pekko Management is on the classpath: one free port per node.
                    "spring.actor.pekko.management.http.hostname=127.0.0.1",
                    "spring.actor.pekko.management.http.port=" + freePort(),
                    "spring.actor.shutdown.exit-process=false"));
            args.addAll(properties);
            args.addAll(nodeProperties.getOrDefault(number, List.of()));
            Members members = new Members();
            SpringApplicationBuilder application = new SpringApplicationBuilder(sources)
                    .web(WebApplicationType.NONE)
                    .listeners(members);
            ClassLoader classLoader = classLoaders.get(number);
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            if (classLoader != null) {
                application.resourceLoader(new DefaultResourceLoader(classLoader));
                // The actor system takes the context class loader of the thread that creates it.
                thread.setContextClassLoader(classLoader);
            }
            try {
                // Command-line arguments: they override the application's own property files.
                ConfigurableApplicationContext context =
                        application.run(args.stream().map(arg -> "--" + arg).toArray(String[]::new));
                return new Node(context, members);
            } finally {
                thread.setContextClassLoader(previous);
            }
        }

        private static int freePort() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
