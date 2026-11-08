package io.github.seonwkim.actor.internal;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorSystemBuilder;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pekko.actor.BootstrapSetup;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.typed.ActorSystem;
import org.springframework.beans.factory.BeanFactory;
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
    /** Application beans the engine may use (the cluster module reads the ObjectMapper) */
    @Nullable private BeanFactory beanFactory;
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
    /** Application beans the engine may use, such as the {@code ObjectMapper} for serialization. */
    public DefaultSpringActorSystemBuilder withBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        return this;
    }

    @Override
    public SpringActorSystem build() {
        if (supplier == null) {
            throw new IllegalStateException(
                    "RootGuardianSupplierWrapper is not set. Call withRootGuardianSupplier() before build().");
        }

        // The cluster module, when present, adds serialization (messages cross nodes only there).
        final ClusterSupport found = ClusterSupport.find().orElse(null);
        final Map<String, Object> withDefaults = found == null ? configMap : found.withDefaults(configMap);
        final Config config =
                ConfigFactory.parseMap(ConfigValueFactory.fromMap(withDefaults)).withFallback(ConfigFactory.load());
        final String name = config.hasPath("pekko.name") ? config.getString("pekko.name") : DEFAULT_SYSTEM_NAME;

        final boolean isClusterMode = Objects.equals(config.getString("pekko.actor.provider"), "cluster");
        // Fail with the missing module's name before Pekko fails on its missing classes.
        final ClusterSupport clusterSupport = !isClusterMode ? null : found != null ? found : ClusterSupport.require();

        ActorSystemSetup setup = ActorSystemSetup.create(BootstrapSetup.create(config));
        if (found != null) {
            setup = found.withSetup(setup, beanFactory);
        }
        final ActorSystem<RootGuardian.Command> actorSystem = ActorSystem.create(supplier.get(), name, setup);

        if (clusterSupport == null) {
            return new PekkoActorSystem(actorSystem);
        }

        if (applicationEventPublisher == null) {
            throw new IllegalArgumentException("ApplicationEventPublisher is not set");
        }
        // Sharded actors are registered later, once every bean exists (initializeClusterSharding).
        return new PekkoActorSystem(actorSystem, clusterSupport.start(actorSystem, applicationEventPublisher));
    }
}
