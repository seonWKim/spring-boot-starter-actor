package io.github.seonwkim.core.impl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.RootGuardianSupplierWrapper;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringActorSystemBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default implementation of the SpringActorSystemBuilder interface. This class builds
 * SpringActorSystem instances with the configured settings. It supports both local and cluster
 * modes.
 */
public class DefaultSpringActorSystemBuilder implements SpringActorSystemBuilder {

    /** The root guardian supplier wrapper */
    @Nullable private RootGuardianSupplierWrapper supplier;
    /** The configuration map */
    private Map<String, Object> configMap = Collections.emptyMap();
    /** The Spring application event publisher */
    @Nullable private ApplicationEventPublisher applicationEventPublisher;
    /** The default actor system name */
    private final String DEFAULT_SYSTEM_NAME = "system";

    /**
     * Sets the root guardian supplier for the actor system.
     *
     * @param supplier The root guardian supplier wrapper
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withRootGuardianSupplier(RootGuardianSupplierWrapper supplier) {
        this.supplier = supplier;
        return this;
    }

    /**
     * Sets the configuration for the actor system.
     *
     * @param config The configuration map
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withConfig(Map<String, Object> config) {
        this.configMap = config;
        return this;
    }

    /**
     * Sets the application event publisher for the actor system. This is required for cluster mode to
     * publish cluster events.
     *
     * @param applicationEventPublisher The Spring application event publisher
     * @return This builder for method chaining
     */
    @Override
    public SpringActorSystemBuilder withApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        return this;
    }

    /**
     * Builds a SpringActorSystem with the configured settings. This method creates either a local or
     * cluster mode actor system based on the configuration. In cluster mode, it initializes all
     * sharded actors from the static ShardedActorRegistry.
     *
     * @return A new SpringActorSystem
     * @throws IllegalArgumentException If in cluster mode and the application event publisher is not
     *     set
     */
    @Override
    public SpringActorSystem build() {
        if (supplier == null) {
            throw new IllegalStateException(
                    "RootGuardianSupplierWrapper is not set. Call withRootGuardianSupplier() before build().");
        }

        final Config config = ConfigFactory.parseMap(ConfigValueFactory.fromMap(applyDefaultSerializers(configMap)))
                .withFallback(ConfigFactory.load());
        final String name = config.hasPath("pekko.name") ? config.getString("pekko.name") : DEFAULT_SYSTEM_NAME;

        final ActorSystem<RootGuardian.Command> actorSystem = ActorSystem.create(supplier.get(), name, config);
        final boolean isClusterMode = Objects.equals(config.getString("pekko.actor.provider"), "cluster");

        if (!isClusterMode) {
            return new SpringActorSystem(actorSystem);
        }

        if (applicationEventPublisher == null) {
            throw new IllegalArgumentException("ApplicationEventPublisher is not set");
        }

        final Cluster cluster = Cluster.get(actorSystem);
        final ClusterSharding clusterSharding = ClusterSharding.get(actorSystem);
        final ClusterSingleton clusterSingleton = ClusterSingleton.get(actorSystem);
        // Sharded actors will be initialized later by SmartInitializingSingleton
        // after all actor beans are registered in the static registry

        return new SpringActorSystem(
                actorSystem,
                cluster,
                clusterSharding,
                clusterSingleton,
                applicationEventPublisher);
    }

    /**
     * Applies default serializers to the configuration map. This method adds Jackson JSON and CBOR
     * serializers to the configuration if they are not already present. It also adds default
     * serialization bindings for JsonSerializable and CborSerializable interfaces.
     *
     * @param configMap The original configuration map
     * @return A new configuration map with default serializers applied
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyDefaultSerializers(Map<String, Object> configMap) {
        final Map<String, Object> result = new HashMap<>(configMap);

        final String jacksonJsonSerializerName = "jackson-json";
        final String jacksonCborSerializerName = "jackson-cbor";

        // Default serializers
        final Map<String, Object> defaultSerializers = new HashMap<>();
        defaultSerializers.put(
                jacksonJsonSerializerName, "org.apache.pekko.serialization.jackson.JacksonJsonSerializer");
        defaultSerializers.put(
                jacksonCborSerializerName, "org.apache.pekko.serialization.jackson.JacksonCborSerializer");

        // Default bindings
        final Map<String, Object> defaultBindings = new HashMap<>();
        defaultBindings.put("io.github.seonwkim.core.serialization.JsonSerializable", jacksonJsonSerializerName);
        defaultBindings.put("io.github.seonwkim.core.serialization.CborSerializable", jacksonCborSerializerName);

        // Get or create pekko.actor.serializers
        Map<String, Object> pekko = (Map<String, Object>) result.computeIfAbsent("pekko", k -> new HashMap<>());
        Map<String, Object> actor = (Map<String, Object>) pekko.computeIfAbsent("actor", k -> new HashMap<>());
        Map<String, Object> serializers =
                (Map<String, Object>) actor.computeIfAbsent("serializers", k -> new HashMap<>());
        Map<String, Object> bindings =
                (Map<String, Object>) actor.computeIfAbsent("serialization-bindings", k -> new HashMap<>());

        // Merge without overwriting existing entries
        defaultSerializers.forEach(serializers::putIfAbsent);
        defaultBindings.forEach(bindings::putIfAbsent);

        return result;
    }
}
