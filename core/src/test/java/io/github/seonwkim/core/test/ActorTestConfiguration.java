package io.github.seonwkim.core.test;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for actor testing support.
 *
 * <p>This configuration is automatically imported by the {@link EnableActorTesting} annotation.
 * It creates and manages the lifecycle of the {@link SpringActorTestKit} bean.
 */
@Configuration
public class ActorTestConfiguration {

    /**
     * Creates a SpringActorTestKit bean if one doesn't already exist.
     *
     * @return A new SpringActorTestKit instance
     */
    @Bean
    @ConditionalOnMissingBean(SpringActorTestKit.class)
    public SpringActorTestKit springActorTestKit() {
        return new SpringActorTestKit();
    }

    /**
     * Creates a lifecycle bean to manage the test kit's shutdown.
     *
     * @param testKit The SpringActorTestKit to manage
     * @return A lifecycle manager for the test kit
     */
    @Bean
    public ActorTestKitLifecycle actorTestKitLifecycle(SpringActorTestKit testKit) {
        return new ActorTestKitLifecycle(testKit);
    }

    /**
     * Manages the lifecycle of the SpringActorTestKit, ensuring proper shutdown when the Spring
     * context is destroyed.
     */
    static class ActorTestKitLifecycle implements DisposableBean {

        private final SpringActorTestKit testKit;

        ActorTestKitLifecycle(SpringActorTestKit testKit) {
            this.testKit = testKit;
        }

        @Override
        public void destroy() {
            testKit.close();
        }
    }
}
