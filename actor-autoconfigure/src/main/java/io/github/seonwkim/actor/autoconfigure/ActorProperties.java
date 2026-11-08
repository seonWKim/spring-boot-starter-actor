package io.github.seonwkim.actor.autoconfigure;

import io.github.seonwkim.actor.ActorConstants;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed settings of the actor system. Each one sets a Pekko key; a raw key under
 * {@code spring.actor.pekko.*} sets the same Pekko key directly and wins, with a warning.
 */
@ConfigurationProperties(prefix = "spring.actor")
public class ActorProperties {

    private static final Logger log = LoggerFactory.getLogger(ActorProperties.class);

    /** Default shutdown timeout: fits inside a 30s Kubernetes termination grace period. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = ActorConstants.DEFAULT_SHUTDOWN_TIMEOUT;

    /** How actors reach each other. */
    public enum Provider {
        /** All actors run in this JVM. */
        LOCAL,
        /** Actors can be reached from other nodes, without cluster membership. */
        REMOTE,
        /** The node joins a cluster: sharding, singletons and cluster events need it. */
        CLUSTER
    }

    /** Name of the actor system (Pekko's pekko.name). Nodes of one cluster must use the same name. */
    @Nullable private String name;

    /** How actors reach each other: local, remote or cluster (Pekko's pekko.actor.provider). Default local. */
    @Nullable private Provider provider;

    private final Remote remote = new Remote();

    private final Cluster cluster = new Cluster();

    private final Shutdown shutdown = new Shutdown();

    @Nullable public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        if (name != null && !name.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException(
                    "spring.actor.name must start with a letter or digit and contain only letters, digits, '-' and '_', got '"
                            + name + "'");
        }
        this.name = name;
    }

    @Nullable public Provider getProvider() {
        return provider;
    }

    public void setProvider(@Nullable Provider provider) {
        this.provider = provider;
    }

    public Remote getRemote() {
        return remote;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Shutdown getShutdown() {
        return shutdown;
    }

    /** Where this node listens for other nodes (remote and cluster providers). */
    public static class Remote {

        /** Host name or address other nodes use to reach this node (pekko.remote.artery.canonical.hostname). */
        @Nullable private String hostname;

        /** Port this node listens on for other nodes; 0 picks a free port (pekko.remote.artery.canonical.port). */
        @Nullable private Integer port;

        @Nullable public String getHostname() {
            return hostname;
        }

        public void setHostname(@Nullable String hostname) {
            this.hostname = hostname;
        }

        @Nullable public Integer getPort() {
            return port;
        }

        public void setPort(@Nullable Integer port) {
            if (port != null && (port < 0 || port > 65535)) {
                throw new IllegalArgumentException("spring.actor.remote.port must be between 0 and 65535, got " + port);
            }
            this.port = port;
        }
    }

    /** Cluster membership (cluster provider). */
    public static class Cluster {

        /** Nodes to join at startup, as pekko://<name>@<host>:<port> (pekko.cluster.seed-nodes). */
        private List<String> seedNodes = new ArrayList<>();

        /** Roles of this node; sharded actors and singletons can be limited to a role (pekko.cluster.roles). */
        private List<String> roles = new ArrayList<>();

        public List<String> getSeedNodes() {
            return seedNodes;
        }

        public void setSeedNodes(List<String> seedNodes) {
            for (String node : seedNodes) {
                if (!node.startsWith("pekko://")) {
                    throw new IllegalArgumentException(
                            "spring.actor.cluster.seed-nodes are addresses like pekko://<name>@<host>:<port>, got '"
                                    + node + "'");
                }
            }
            this.seedNodes = new ArrayList<>(seedNodes);
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = new ArrayList<>(roles);
        }
    }

    /** Stopping the actor system when the Spring context closes. */
    public static class Shutdown {

        /** How long closing the context waits for the actor system's coordinated shutdown. */
        private Duration timeout = DEFAULT_SHUTDOWN_TIMEOUT;

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("spring.actor.shutdown.timeout must be positive, got " + timeout);
            }
            this.timeout = timeout;
        }
    }

    /** The keys under {@code spring.actor} that are typed settings rather than Pekko configuration. */
    static final List<String> TYPED_ROOTS = List.of("name", "provider", "remote", "cluster", "shutdown");

    /**
     * The Pekko configuration. Every key under {@code spring.actor} except the typed settings is
     * passed through with the prefix removed (so {@code spring.actor.pekko.loglevel} becomes
     * {@code pekko.loglevel}, and a dispatcher defined at {@code spring.actor.my-dispatcher} becomes
     * {@code my-dispatcher}). The typed settings fill in their Pekko keys; a raw key that sets a
     * different value wins and is logged as a warning.
     *
     * @param springActor the keys bound under {@code spring.actor}
     * @return the configuration to give Pekko
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toPekkoConfig(Map<String, Object> springActor) {
        Map<String, Object> config = normalizeCommaSeparatedLists(springActor);
        TYPED_ROOTS.forEach(config::remove);
        Object raw = config.get("pekko");
        Map<String, Object> pekko = raw instanceof Map ? (Map<String, Object>) raw : new LinkedHashMap<>();
        for (TypedKey typed : typedKeys()) {
            Object rawValue = lookup(pekko, typed.pekkoPath());
            if (rawValue instanceof String single && typed.value() instanceof List) {
                // Pekko reads this key as a list: a raw single value means a one-item list.
                rawValue = List.of(single);
                put(pekko, typed.pekkoPath(), rawValue);
            }
            if (rawValue == null) {
                put(pekko, typed.pekkoPath(), typed.value());
            } else if (!String.valueOf(rawValue).equals(String.valueOf(typed.value()))) {
                log.warn(
                        "spring.actor.pekko.{}={} overrides spring.actor.{}={}",
                        typed.pekkoPath(),
                        rawValue,
                        typed.springKey(),
                        typed.value());
            }
        }
        config.put("pekko", pekko);
        return config;
    }

    private record TypedKey(String springKey, String pekkoPath, Object value) {}

    private List<TypedKey> typedKeys() {
        List<TypedKey> keys = new ArrayList<>();
        if (name != null) {
            keys.add(new TypedKey("name", "name", name));
        }
        if (provider != null) {
            keys.add(new TypedKey("provider", "actor.provider", provider.name().toLowerCase(Locale.ROOT)));
        }
        if (remote.hostname != null) {
            keys.add(new TypedKey("remote.hostname", "remote.artery.canonical.hostname", remote.hostname));
        }
        if (remote.port != null) {
            keys.add(new TypedKey("remote.port", "remote.artery.canonical.port", remote.port));
        }
        if (!cluster.seedNodes.isEmpty()) {
            keys.add(new TypedKey("cluster.seed-nodes", "cluster.seed-nodes", cluster.seedNodes));
        }
        if (!cluster.roles.isEmpty()) {
            keys.add(new TypedKey("cluster.roles", "cluster.roles", cluster.roles));
        }
        return keys;
    }

    @Nullable private static Object lookup(Map<String, Object> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    /** Copies the map; a string with commas (not a HOCON list literal) becomes a list. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeCommaSeparatedLists(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                normalized.put(entry.getKey(), normalizeCommaSeparatedLists((Map<String, Object>) value));
            } else if (value instanceof String str) {
                String trimmed = str.trim();
                if (trimmed.contains(",") && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    normalized.put(
                            entry.getKey(),
                            Arrays.stream(trimmed.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .toList());
                } else {
                    normalized.put(entry.getKey(), trimmed);
                }
            } else {
                normalized.put(entry.getKey(), Objects.requireNonNull(value));
            }
        }
        return normalized;
    }
}
