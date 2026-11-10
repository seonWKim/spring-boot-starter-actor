package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.delivery.ShardedReliableSender;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.ServiceLoader;

/** Reliable senders to sharded entities, provided by {@code actor-cluster} through {@link ServiceLoader}. */
public interface ShardedDeliverySupport {

    <T> ShardedReliableSender<T> start(
            SpringActorSystem system, Class<? extends SpringShardedActor<T>> actorClass, String producerId);

    /** The support on the classpath, or a failure that names the missing module. */
    static ShardedDeliverySupport require() {
        return ServiceLoader.load(ShardedDeliverySupport.class, ShardedDeliverySupport.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Reliable delivery to sharded actors needs the actor-cluster module: depend on"
                                + " io.github.seonwkim:actor-spring-boot-starter-cluster"));
    }
}
