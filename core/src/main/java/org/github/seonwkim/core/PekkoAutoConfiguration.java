package org.github.seonwkim.core;

import org.github.seonwkim.core.impl.DefaultActorSystemBuilder;
import org.github.seonwkim.core.impl.DefaultActorSystemInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PekkoProperties.class)
@ConditionalOnProperty(prefix = "actor.pekko", name = "enabled", havingValue = "true")
@ComponentScan(basePackages = "org.github.seonwkim.core")
public class PekkoAutoConfiguration {

    @Bean
    public ActorSystemBuilder actorSystemBuilder(
            PekkoProperties properties,
            RootGuardianSupplierWrapper rootGuardianSupplierWrapper) {
        return new DefaultActorSystemBuilder()
                .withName("spring-boot-actor-system")
                .withConfig(properties.getConfig())
                .withRootGuardianSupplier(rootGuardianSupplierWrapper.getSupplier());
    }

    @Bean
    public ActorSystemInstance actorSystem(ActorSystemBuilder builder) {
        return new DefaultActorSystemInstance(builder.build());
    }

    @Bean
    public RootGuardianSupplierWrapper rootGuardianSupplierWrapper(ActorTypeRegistry actorTypeRegistry) {
        return new RootGuardianSupplierWrapper(() -> RootGuardian.create(actorTypeRegistry));
    }

    @Bean
    public ActorTypeRegistry actorTypeRegistry() {
        // TODO
        return new ActorTypeRegistry();
    }
}
