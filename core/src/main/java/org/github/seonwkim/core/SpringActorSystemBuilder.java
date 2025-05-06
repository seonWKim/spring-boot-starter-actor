package org.github.seonwkim.core;

import java.util.Map;

import org.github.seonwkim.core.shard.ShardedActorRegistry;
import org.springframework.context.ApplicationEventPublisher;

public interface SpringActorSystemBuilder {

    SpringActorSystemBuilder withRootGuardianSupplier(RootGuardianSupplierWrapper supplier);

    SpringActorSystemBuilder withConfig(Map<String, Object> config);

    SpringActorSystemBuilder withApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher);

    SpringActorSystemBuilder withShardedActorRegistry(ShardedActorRegistry shardedActorRegistry);

    SpringActorSystem build();
}
