package io.github.seonwkim.metrics.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;

/**
 * Ensures the core's actorSystem bean depends on actorMetricsRegistry when both exist,
 * so the registry is set before the ActorSystem is created.
 *
 * <p>Runs after all bean definitions are loaded. When actorMetricsRegistry exists (from
 * ActorMetricsAutoConfiguration), adds {@code depends-on="actorMetricsRegistry"} to the
 * actorSystem bean definition.
 */
public class ActorMetricsBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ActorMetricsBeanDefinitionRegistryPostProcessor.class);

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition("actorMetricsRegistry")) {
            logger.debug("actorMetricsRegistry not found; skipping depends-on injection");
            return;
        }
        String actorSystemName = "actorSystem";
        if (!registry.containsBeanDefinition(actorSystemName)) {
            logger.debug("actorSystem not found; skipping depends-on injection");
            return;
        }
        BeanDefinition bd = registry.getBeanDefinition(actorSystemName);
        bd.setDependsOn(mergeDependsOn("actorMetricsRegistry", bd.getDependsOn()));
        logger.debug("Added actorMetricsRegistry as depends-on for actorSystem");
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No-op
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String[] mergeDependsOn(String newDep, String[] existing) {
        if (existing == null || existing.length == 0) {
            return new String[] {newDep};
        }
        for (String e : existing) {
            if (newDep.equals(e)) {
                return existing;
            }
        }
        String[] result = new String[existing.length + 1];
        result[0] = newDep;
        System.arraycopy(existing, 0, result, 1, existing.length);
        return result;
    }
}
