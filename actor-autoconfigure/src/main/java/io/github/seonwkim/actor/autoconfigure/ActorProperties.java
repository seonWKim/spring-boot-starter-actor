package io.github.seonwkim.actor.autoconfigure;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValueType;
import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.internal.SpringExecutorConfigurator;
import io.github.seonwkim.actor.internal.delivery.ReliableConsumerModule;
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
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.util.ClassUtils;

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

    /**
     * Whether the starter starts an actor system. False turns off every actor auto-configuration
     * while the starter stays on the classpath.
     */
    private boolean enabled = true;

    /** Name of the actor system (Pekko's pekko.name). Nodes of one cluster must use the same name. */
    @Nullable private String name;

    /** How actors reach each other: local, remote or cluster (Pekko's pekko.actor.provider). Default local. */
    @Nullable private Provider provider;

    private final Remote remote = new Remote();

    private final Cluster cluster = new Cluster();

    private final Shutdown shutdown = new Shutdown();

    private final Jdbc jdbc = new Jdbc();

    private final Persistence persistence = new Persistence();

    private final VirtualThreads virtualThreads = new VirtualThreads();

    private final Delivery delivery = new Delivery();

    /**
     * Dispatchers that run on Spring Executor beans, by dispatcher name. An actor uses one with
     * withDispatcherFromConfig("<name>").
     */
    private final Map<String, Dispatcher> dispatchers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public Jdbc getJdbc() {
        return jdbc;
    }

    public Persistence getPersistence() {
        return persistence;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public Map<String, Dispatcher> getDispatchers() {
        return dispatchers;
    }

    public VirtualThreads getVirtualThreads() {
        return virtualThreads;
    }

    /** Receivers of reliable delivery (ReliableDelivery.consumer). */
    public static class Delivery {

        /**
         * How long a receiver remembers the idempotency key of a message it handled; a message with a
         * remembered key is acknowledged again without running the handler. A receiver can override it.
         * Default 10m.
         */
        @Nullable private Duration idempotencyTtl;

        /** The most keys one receiver remembers; past it, the oldest are forgotten first. Default 10000. */
        @Nullable private Integer maxRememberedKeys;

        @Nullable public Duration getIdempotencyTtl() {
            return idempotencyTtl;
        }

        public void setIdempotencyTtl(Duration idempotencyTtl) {
            if (idempotencyTtl.isNegative() || idempotencyTtl.isZero()) {
                throw new IllegalArgumentException(
                        "spring.actor.delivery.idempotency-ttl must be positive, got " + idempotencyTtl);
            }
            this.idempotencyTtl = idempotencyTtl;
        }

        @Nullable public Integer getMaxRememberedKeys() {
            return maxRememberedKeys;
        }

        public void setMaxRememberedKeys(int maxRememberedKeys) {
            if (maxRememberedKeys < 1) {
                throw new IllegalArgumentException(
                        "spring.actor.delivery.max-remembered-keys must be at least 1, got " + maxRememberedKeys);
            }
            this.maxRememberedKeys = maxRememberedKeys;
        }
    }

    /** Running blocking work on virtual threads. */
    public static class VirtualThreads {

        /** Whether virtual threads are used. */
        public enum Mode {
            /** Follow Spring Boot's spring.threads.virtual.enabled. */
            AUTO,
            /** Use virtual threads. */
            TRUE,
            /** Use platform threads. */
            FALSE
        }

        /**
         * Runs the blocking dispatcher (withBlockingDispatcher()) on virtual threads. auto follows
         * spring.threads.virtual.enabled. The default dispatcher always stays on platform threads.
         */
        private Mode enabled = Mode.AUTO;

        public Mode getEnabled() {
            return enabled;
        }

        public void setEnabled(Mode enabled) {
            this.enabled = enabled;
        }

        boolean isOn(boolean springThreadsVirtual) {
            return enabled == Mode.TRUE || (enabled == Mode.AUTO && springThreadsVirtual);
        }
    }

    /** A dispatcher that runs its actors on a Spring Executor bean or on virtual threads. */
    public static class Dispatcher {

        /** Name of the Executor bean (for example a ThreadPoolTaskExecutor) the actors run on. */
        @Nullable private String executor;

        /** Runs the actors on virtual threads, one per task, instead of an Executor bean. */
        private boolean virtual;

        public boolean isVirtual() {
            return virtual;
        }

        public void setVirtual(boolean virtual) {
            this.virtual = virtual;
        }

        /** Messages an actor processes before its thread goes to the next actor. Pekko's default is 5. */
        @Nullable private Integer throughput;

        @Nullable public String getExecutor() {
            return executor;
        }

        public void setExecutor(@Nullable String executor) {
            this.executor = executor;
        }

        @Nullable public Integer getThroughput() {
            return throughput;
        }

        public void setThroughput(@Nullable Integer throughput) {
            if (throughput != null && throughput < 1) {
                throw new IllegalArgumentException(
                        "spring.actor.dispatchers.*.throughput must be at least 1, got " + throughput);
            }
            this.throughput = throughput;
        }
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

        /** How a node finds the cluster to join. */
        public enum Join {
            /** Join the nodes listed in seed-nodes (or itself, with join-self). */
            SEED_NODES,
            /** Find peers through discovery.method and form or join the cluster with Cluster Bootstrap. */
            DISCOVERY
        }

        /**
         * How this node finds the cluster: seed-nodes (the listed nodes) or discovery (peers found
         * through spring.actor.cluster.discovery.method, with Pekko Cluster Bootstrap).
         */
        private Join join = Join.SEED_NODES;

        private final Discovery discovery = new Discovery();

        private final Downing downing = new Downing();

        private final Lease lease = new Lease();

        private final Singleton singleton = new Singleton();

        private final LeaseUser sharding = new LeaseUser();

        public Downing getDowning() {
            return downing;
        }

        public Lease getLease() {
            return lease;
        }

        /** Cluster singletons. */
        public Singleton getSingleton() {
            return singleton;
        }

        /** Cluster sharding. */
        public LeaseUser getSharding() {
            return sharding;
        }

        public Join getJoin() {
            return join;
        }

        public void setJoin(Join join) {
            this.join = join;
        }

        public Discovery getDiscovery() {
            return discovery;
        }

        /** Nodes to join at startup, as pekko://<name>@<host>:<port> (pekko.cluster.seed-nodes). */
        private List<String> seedNodes = new ArrayList<>();

        /**
         * Roles of this node; sharded actors and singletons (singleton.role) can be limited to a role
         * (pekko.cluster.roles).
         */
        private List<String> roles = new ArrayList<>();

        /**
         * Join this node to itself at startup, forming a one-node cluster without seed nodes. For
         * local runs and tests, where the port may be 0; not with seed-nodes.
         */
        private boolean joinSelf;

        public boolean isJoinSelf() {
            return joinSelf;
        }

        public void setJoinSelf(boolean joinSelf) {
            this.joinSelf = joinSelf;
        }

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

    /** What the cluster does with nodes it can no longer reach (Pekko's split brain resolver). */
    public static class Downing {

        /** The split brain resolver strategy, or none. */
        public enum Strategy {
            /** The side with more nodes stays; on a tie, the side with the lowest address. */
            KEEP_MAJORITY,
            /** The side that acquires a lease (spring.actor.cluster.lease.store) stays. */
            LEASE_MAJORITY,
            /** A side with at least quorum-size nodes stays. */
            STATIC_QUORUM,
            /** The side with the oldest node stays. */
            KEEP_OLDEST,
            /** All nodes are downed. */
            DOWN_ALL,
            /** No downing: unreachable nodes stay members until removed by hand. */
            NONE
        }

        /**
         * The split brain resolver strategy: keep-majority, lease-majority, static-quorum,
         * keep-oldest, down-all or none. Default keep-majority with join=discovery, none otherwise.
         */
        @Nullable private Strategy strategy;

        /** Nodes a side needs to stay up (static-quorum). Set it above half the cluster's size. */
        @Nullable private Integer quorumSize;

        @Nullable public Strategy getStrategy() {
            return strategy;
        }

        public void setStrategy(@Nullable Strategy strategy) {
            this.strategy = strategy;
        }

        @Nullable public Integer getQuorumSize() {
            return quorumSize;
        }

        public void setQuorumSize(@Nullable Integer quorumSize) {
            if (quorumSize != null && quorumSize < 1) {
                throw new IllegalArgumentException(
                        "spring.actor.cluster.downing.quorum-size must be at least 1, got " + quorumSize);
            }
            this.quorumSize = quorumSize;
        }
    }

    /** Leases on a LeaseStore, for lease-majority downing, singletons and shards. */
    public static class Lease {

        /** Where leases are kept. */
        public enum Store {
            /** The actor_lease table on the application's DataSource (actor-jdbc). */
            JDBC,
            /** Keys with a TTL on the application's RedisConnectionFactory (actor-redis). */
            REDIS,
            /** The application's own LeaseStore bean. */
            CUSTOM
        }

        /**
         * Where leases are kept: jdbc, redis or custom (your LeaseStore bean). Needed by
         * downing.strategy=lease-majority, singleton.use-lease and sharding.use-lease.
         */
        @Nullable private Store store;

        /** How long a lease is held after its last renewal before another node may take it. */
        private Duration ttl = Duration.ofSeconds(120);

        /** How often a held lease is renewed. */
        private Duration renewInterval = Duration.ofSeconds(12);

        /**
         * A holder counts its lease as lost this long before the ttl runs out since its last
         * renewal, so it stops before another node can take the lease.
         */
        private Duration expiryMargin = Duration.ofSeconds(10);

        @Nullable public Store getStore() {
            return store;
        }

        public void setStore(@Nullable Store store) {
            this.store = store;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = positive("lease.ttl", ttl);
        }

        public Duration getRenewInterval() {
            return renewInterval;
        }

        public void setRenewInterval(Duration renewInterval) {
            this.renewInterval = positive("lease.renew-interval", renewInterval);
        }

        public Duration getExpiryMargin() {
            return expiryMargin;
        }

        public void setExpiryMargin(Duration expiryMargin) {
            this.expiryMargin = positive("lease.expiry-margin", expiryMargin);
        }

        private static Duration positive(String key, Duration value) {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("spring.actor.cluster." + key + " must be positive, got " + value);
            }
            return value;
        }
    }

    /** A cluster feature that can run only while it holds a lease. */
    public static class LeaseUser {

        /**
         * Run only while holding a lease from spring.actor.cluster.lease.store, so two nodes never
         * run it at once, even during a network split.
         */
        private boolean useLease;

        public boolean isUseLease() {
            return useLease;
        }

        public void setUseLease(boolean useLease) {
            this.useLease = useLease;
        }
    }

    /** Cluster singletons: where they run, and whether they hold a lease. */
    public static class Singleton extends LeaseUser {

        /**
         * Run singletons only on nodes with this role (one of their spring.actor.cluster.roles);
         * every node still gets a proxy. Set the same value on every node. Default: any node.
         */
        @Nullable private String role;

        @Nullable public String getRole() {
            return role;
        }

        public void setRole(@Nullable String role) {
            this.role = role;
        }
    }

    /** Finding peers when spring.actor.cluster.join=discovery. */
    public static class Discovery {

        /** Where peers are found. */
        public enum Method {
            /** The actor_peer table on the application's DataSource (actor-jdbc). */
            JDBC,
            /** Keys with a TTL on the application's RedisConnectionFactory (actor-redis). */
            REDIS,
            /** The application's own PeerRegistry bean. */
            CUSTOM,
            /** The Kubernetes API (pekko-discovery-kubernetes-api; needs RBAC to list pods). */
            KUBERNETES_API,
            /** DNS SRV or A records (Pekko's pekko-dns method). */
            DNS,
            /** A static list under pekko.discovery.config.services (Pekko's config method). */
            CONFIG
        }

        /**
         * Where peers are found: jdbc, redis, custom (your PeerRegistry bean), kubernetes-api, dns
         * or config. A PeerRegistry bean of your own replaces the jdbc and redis stores.
         */
        @Nullable private Method method;

        /** How often a node renews its own entry in the peer registry (jdbc, redis, custom). */
        private Duration heartbeatInterval = Duration.ofSeconds(5);

        /** How long an entry counts as alive after its last renewal (jdbc, redis, custom). */
        private Duration ttl = Duration.ofSeconds(30);

        @Nullable public Method getMethod() {
            return method;
        }

        public void setMethod(@Nullable Method method) {
            this.method = method;
        }

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
                throw new IllegalArgumentException(
                        "spring.actor.cluster.discovery.heartbeat-interval must be positive, got " + heartbeatInterval);
            }
            this.heartbeatInterval = heartbeatInterval;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("spring.actor.cluster.discovery.ttl must be positive, got " + ttl);
            }
            this.ttl = ttl;
        }
    }

    /** The tables of actor-jdbc (actor_peer, actor_lease). */
    public static class Jdbc {

        /**
         * When to create the actor-jdbc tables (actor_peer, actor_lease): embedded (only in an
         * embedded database), always, or never (you create them from the schema-*.sql files in actor-jdbc).
         */
        private DatabaseInitializationMode initializeSchema = DatabaseInitializationMode.EMBEDDED;

        public DatabaseInitializationMode getInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(DatabaseInitializationMode initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }

    /** The tables of actor-persistence (journal, snapshots) and actor-projection (offsets). */
    public static class Persistence {

        /**
         * When to create the actor-persistence tables (event_journal, event_tag, snapshot) and the
         * actor-projection offset tables: embedded (only in an embedded database), always, or never (you
         * create them from the schema-*.sql files in those modules).
         */
        private DatabaseInitializationMode initializeSchema = DatabaseInitializationMode.EMBEDDED;

        public DatabaseInitializationMode getInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(DatabaseInitializationMode initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }

    /** Stopping the actor system when the Spring context closes. */
    public static class Shutdown {

        /** How long closing the context waits for the actor system's coordinated shutdown. */
        private Duration timeout = DEFAULT_SHUTDOWN_TIMEOUT;

        /**
         * When the actor system terminates by itself (for example after this node is downed), the
         * context closes; with this set, the process then exits with status 1 so an orchestrator
         * restarts it.
         */
        private boolean exitProcess = true;

        public boolean isExitProcess() {
            return exitProcess;
        }

        public void setExitProcess(boolean exitProcess) {
            this.exitProcess = exitProcess;
        }

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
    static final List<String> TYPED_ROOTS = List.of(
            "enabled",
            "name",
            "provider",
            "remote",
            "cluster",
            "shutdown",
            "dispatchers",
            "virtual-threads",
            "jdbc",
            "persistence",
            "metrics",
            "delivery");

    /**
     * The Pekko configuration. Every key under {@code spring.actor} except the typed settings is
     * passed through with the prefix removed (so {@code spring.actor.pekko.loglevel} becomes
     * {@code pekko.loglevel}, and a dispatcher defined at {@code spring.actor.my-dispatcher} becomes
     * {@code my-dispatcher}). The typed settings fill in their Pekko keys; a raw key that sets a
     * different value wins and is logged as a warning.
     *
     * @param springActor the keys bound under {@code spring.actor}
     * @param springThreadsVirtual the value of {@code spring.threads.virtual.enabled}, which
     *     {@code spring.actor.virtual-threads.enabled=auto} follows
     * @return the configuration to give Pekko
     */
    public Map<String, Object> toPekkoConfig(Map<String, Object> springActor, boolean springThreadsVirtual) {
        Map<String, Object> config = normalizeCommaSeparatedLists(springActor);
        TYPED_ROOTS.forEach(config::remove);
        wrapSingleValuesOfListKeys(config, ConfigFactory.defaultReference(), List.of());
        for (TypedKey typed : typedKeys(springThreadsVirtual)) {
            Object rawValue = lookup(config, typed.configPath());
            if (rawValue == null) {
                put(config, typed.configPath(), typed.value());
            } else if (!String.valueOf(rawValue).equals(String.valueOf(typed.value()))) {
                log.warn(
                        "spring.actor.{}={} overrides spring.actor.{}={}",
                        typed.configPath(),
                        rawValue,
                        typed.springKey(),
                        typed.value());
            }
        }
        config.computeIfAbsent("pekko", key -> new LinkedHashMap<String, Object>());
        if ("cluster".equals(String.valueOf(lookup(config, "pekko.actor.provider")))
                && lookup(config, "pekko.cluster.downing-provider-class") == null) {
            log.warn("This cluster has no downing: after a network split or a crash, unreachable nodes stay members"
                    + " and both sides of a split keep running. Set spring.actor.cluster.downing.strategy"
                    + " (keep-majority, lease-majority, static-quorum, keep-oldest or down-all)");
        }
        return config;
    }

    /** Read by the cluster module, which joins the node to itself when it is true. */
    private static final String JOIN_SELF_KEY = "spring-actor.cluster.join-self";

    /** The discovery method of actor-cluster's PeerRegistry adapter (PeerRegistryServiceDiscovery.METHOD). */
    private static final String PEER_REGISTRY_METHOD = "spring-actor-peer-registry";

    private static final String CLUSTER_BOOTSTRAP = "org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap";

    /** Pekko's executor that starts one virtual thread per task (Java 21+). */
    private static final String VIRTUAL_EXECUTOR = "virtual-thread-executor";

    /** A typed setting and the config path (from the root, like {@code pekko.name}) it sets. */
    private record TypedKey(String springKey, String configPath, Object value) {}

    private List<TypedKey> typedKeys(boolean springThreadsVirtual) {
        List<TypedKey> keys = new ArrayList<>();
        if (virtualThreads.isOn(springThreadsVirtual)) {
            keys.add(new TypedKey(
                    "virtual-threads.enabled",
                    "pekko.actor.default-blocking-io-dispatcher.executor",
                    VIRTUAL_EXECUTOR));
        }
        if (name != null) {
            keys.add(new TypedKey("name", "pekko.name", name));
        }
        if (provider != null) {
            keys.add(new TypedKey(
                    "provider", "pekko.actor.provider", provider.name().toLowerCase(Locale.ROOT)));
        }
        if (remote.hostname != null) {
            keys.add(new TypedKey("remote.hostname", "pekko.remote.artery.canonical.hostname", remote.hostname));
        }
        if (remote.port != null) {
            keys.add(new TypedKey("remote.port", "pekko.remote.artery.canonical.port", remote.port));
        }
        if (!cluster.seedNodes.isEmpty()) {
            keys.add(new TypedKey("cluster.seed-nodes", "pekko.cluster.seed-nodes", cluster.seedNodes));
        }
        if (cluster.joinSelf) {
            if (!cluster.seedNodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "spring.actor.cluster.join-self and spring.actor.cluster.seed-nodes cannot both be set");
            }
            keys.add(new TypedKey("cluster.join-self", JOIN_SELF_KEY, true));
        }
        if (cluster.join == Cluster.Join.DISCOVERY) {
            keys.addAll(discoveryKeys());
        }
        keys.addAll(downingAndLeaseKeys());
        if (delivery.idempotencyTtl != null) {
            keys.add(new TypedKey(
                    "delivery.idempotency-ttl",
                    ReliableConsumerModule.TTL_KEY,
                    delivery.idempotencyTtl.toMillis() + "ms"));
        }
        if (delivery.maxRememberedKeys != null) {
            keys.add(new TypedKey(
                    "delivery.max-remembered-keys", ReliableConsumerModule.MAX_KEYS_KEY, delivery.maxRememberedKeys));
        }
        if (!cluster.roles.isEmpty()) {
            keys.add(new TypedKey("cluster.roles", "pekko.cluster.roles", cluster.roles));
        }
        if (cluster.singleton.role != null && !cluster.singleton.role.isBlank()) {
            keys.add(new TypedKey("cluster.singleton.role", "pekko.cluster.singleton.role", cluster.singleton.role));
        }
        dispatchers.forEach((dispatcherName, dispatcher) -> {
            String prefix = "dispatchers." + dispatcherName + ".";
            if (!dispatcherName.matches("[A-Za-z0-9][A-Za-z0-9_-]*")
                    || TYPED_ROOTS.contains(dispatcherName)
                    || dispatcherName.equals("pekko")) {
                throw new IllegalArgumentException("spring.actor.dispatchers." + dispatcherName
                        + ": a dispatcher name is letters, digits, '-' and '_', and not one of pekko, " + TYPED_ROOTS);
            }
            if (dispatcher.virtual) {
                if (dispatcher.executor != null) {
                    throw new IllegalArgumentException("spring.actor.dispatchers." + dispatcherName
                            + " sets both executor and virtual; choose one");
                }
                keys.add(new TypedKey(prefix + "virtual", dispatcherName + ".type", "Dispatcher"));
                keys.add(new TypedKey(prefix + "virtual", dispatcherName + ".executor", VIRTUAL_EXECUTOR));
            } else {
                if (dispatcher.executor == null) {
                    throw new IllegalArgumentException(
                            "spring.actor." + prefix + "executor must name an Executor bean (or set virtual: true)");
                }
                keys.add(new TypedKey(prefix + "executor", dispatcherName + ".type", "Dispatcher"));
                keys.add(new TypedKey(
                        prefix + "executor", dispatcherName + ".executor", SpringExecutorConfigurator.class.getName()));
                keys.add(new TypedKey(
                        prefix + "executor",
                        dispatcherName + "." + SpringExecutorConfigurator.BEAN_KEY,
                        dispatcher.executor));
            }
            if (dispatcher.throughput != null) {
                keys.add(new TypedKey(prefix + "throughput", dispatcherName + ".throughput", dispatcher.throughput));
            }
        });
        return keys;
    }

    /** The lease config path of actor-cluster's LeaseStore adapter (LeaseStoreLease.CONFIG_PATH). */
    private static final String LEASE_PATH = "spring-actor.lease";

    private static final String SPLIT_BRAIN_RESOLVER = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider";

    /** The downing strategy in effect: the set one, or keep-majority with discovery, or none. */
    Downing.Strategy downingStrategy() {
        Downing.Strategy strategy = cluster.downing.strategy;
        if (strategy != null) {
            return strategy;
        }
        return cluster.join == Cluster.Join.DISCOVERY ? Downing.Strategy.KEEP_MAJORITY : Downing.Strategy.NONE;
    }

    private List<TypedKey> downingAndLeaseKeys() {
        List<TypedKey> keys = new ArrayList<>();
        Downing.Strategy strategy = downingStrategy();
        if (strategy != Downing.Strategy.NONE) {
            String name = strategy.name().toLowerCase(Locale.ROOT).replace('_', '-');
            keys.add(new TypedKey(
                    "cluster.downing.strategy", "pekko.cluster.downing-provider-class", SPLIT_BRAIN_RESOLVER));
            keys.add(new TypedKey(
                    "cluster.downing.strategy", "pekko.cluster.split-brain-resolver.active-strategy", name));
        }
        if (strategy == Downing.Strategy.STATIC_QUORUM) {
            if (cluster.downing.quorumSize == null) {
                throw new IllegalArgumentException(
                        "spring.actor.cluster.downing.strategy=static-quorum needs spring.actor.cluster.downing.quorum-size");
            }
            keys.add(new TypedKey(
                    "cluster.downing.quorum-size",
                    "pekko.cluster.split-brain-resolver.static-quorum.quorum-size",
                    cluster.downing.quorumSize));
        }
        List<String> leaseUsers = new ArrayList<>();
        if (strategy == Downing.Strategy.LEASE_MAJORITY) {
            leaseUsers.add("downing.strategy=lease-majority");
            keys.add(new TypedKey(
                    "cluster.downing.strategy",
                    "pekko.cluster.split-brain-resolver.lease-majority.lease-implementation",
                    LEASE_PATH));
        }
        if (cluster.singleton.isUseLease()) {
            leaseUsers.add("singleton.use-lease");
            keys.add(new TypedKey("cluster.singleton.use-lease", "pekko.cluster.singleton.use-lease", LEASE_PATH));
        }
        if (cluster.sharding.useLease) {
            leaseUsers.add("sharding.use-lease");
            keys.add(new TypedKey("cluster.sharding.use-lease", "pekko.cluster.sharding.use-lease", LEASE_PATH));
        }
        Lease lease = cluster.lease;
        if (!leaseUsers.isEmpty() && lease.store == null) {
            throw new IllegalArgumentException("spring.actor.cluster." + String.join(", ", leaseUsers)
                    + " needs spring.actor.cluster.lease.store: jdbc, redis or custom");
        }
        if (lease.store != null) {
            if (lease.expiryMargin.compareTo(lease.ttl) >= 0) {
                throw new IllegalArgumentException(
                        "spring.actor.cluster.lease.expiry-margin must be less than lease.ttl");
            }
            keys.add(new TypedKey("cluster.lease.ttl", LEASE_PATH + ".heartbeat-timeout", lease.ttl.toMillis() + "ms"));
            keys.add(new TypedKey(
                    "cluster.lease.renew-interval",
                    LEASE_PATH + ".heartbeat-interval",
                    lease.renewInterval.toMillis() + "ms"));
            keys.add(new TypedKey(
                    "cluster.lease.expiry-margin",
                    LEASE_PATH + ".expiry-margin",
                    lease.expiryMargin.toMillis() + "ms"));
        }
        return keys;
    }

    private List<TypedKey> discoveryKeys() {
        if (!cluster.seedNodes.isEmpty() || cluster.joinSelf) {
            throw new IllegalArgumentException(
                    "spring.actor.cluster.join=discovery finds its peers: remove spring.actor.cluster.seed-nodes and join-self");
        }
        Discovery discovery = cluster.discovery;
        if (discovery.method == null) {
            throw new IllegalArgumentException(
                    "spring.actor.cluster.join=discovery needs spring.actor.cluster.discovery.method:"
                            + " jdbc, redis, custom, kubernetes-api, dns or config");
        }
        if (!ClassUtils.isPresent(CLUSTER_BOOTSTRAP, ActorProperties.class.getClassLoader())) {
            throw new IllegalStateException("spring.actor.cluster.join=discovery needs Pekko Cluster Bootstrap: add"
                    + " org.apache.pekko:pekko-management-cluster-bootstrap_3 and pekko-management-cluster-http_3");
        }
        String key = "cluster.discovery.method";
        return switch (discovery.method) {
            case KUBERNETES_API -> List.of(new TypedKey(key, "pekko.discovery.method", "kubernetes-api"));
            case DNS -> List.of(new TypedKey(key, "pekko.discovery.method", "pekko-dns"));
            case CONFIG -> List.of(new TypedKey(key, "pekko.discovery.method", "config"));
            case JDBC, REDIS, CUSTOM -> {
                String adapter = "pekko.discovery." + PEER_REGISTRY_METHOD;
                yield List.of(
                        new TypedKey(key, "pekko.discovery.method", PEER_REGISTRY_METHOD),
                        new TypedKey(
                                "cluster.discovery.heartbeat-interval",
                                adapter + ".heartbeat-interval",
                                discovery.heartbeatInterval.toMillis() + "ms"),
                        new TypedKey("cluster.discovery.ttl", adapter + ".ttl", discovery.ttl.toMillis() + "ms"),
                        // The registry lists only management ports, so Bootstrap must not drop the
                        // ones that differ from this node's own (several nodes on one host).
                        new TypedKey(
                                key,
                                "pekko.management.cluster.bootstrap.contact-point.filter-on-fallback-port",
                                false));
            }
        };
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

    /**
     * A raw single value for a key that Pekko's reference configuration holds as a list (such as
     * {@code spring.actor.pekko.cluster.seed-nodes=pekko://a@h:1}) becomes a one-item list, as
     * {@code seed-nodes[0]} would.
     */
    @SuppressWarnings("unchecked")
    private static void wrapSingleValuesOfListKeys(Map<String, Object> node, Config reference, List<String> parents) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            List<String> path = new ArrayList<>(parents);
            path.add(entry.getKey());
            if (entry.getValue() instanceof Map) {
                wrapSingleValuesOfListKeys((Map<String, Object>) entry.getValue(), reference, path);
            } else if (entry.getValue() instanceof String single
                    && !(single.startsWith("[") && single.endsWith("]"))
                    && isListInReference(reference, ConfigUtil.joinPath(path))) {
                entry.setValue(List.of(single));
            }
        }
    }

    private static boolean isListInReference(Config reference, String path) {
        try {
            return reference.hasPath(path) && reference.getValue(path).valueType() == ConfigValueType.LIST;
        } catch (ConfigException e) {
            return false;
        }
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
