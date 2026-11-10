package io.github.seonwkim.actor.internal.shard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

class ShardedActorRegistryTest {

    @Test
    void aProxiedShardedActorIsFoundByItsDeclaredClass() {
        // What a @Transactional (or any class-based AOP) sharded actor bean looks like after Spring proxies it.
        ProxyFactory factory = new ProxyFactory(new TestShardedActor());
        factory.setProxyTargetClass(true);
        SpringShardedActor<TestShardedActor.Command> proxy =
                (SpringShardedActor<TestShardedActor.Command>) factory.getProxy();
        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();

        ShardedActorRegistry registry = new ShardedActorRegistry();
        registry.register(proxy);

        assertThat(registry.getByClass(TestShardedActor.class)).isSameAs(proxy);
    }
}
