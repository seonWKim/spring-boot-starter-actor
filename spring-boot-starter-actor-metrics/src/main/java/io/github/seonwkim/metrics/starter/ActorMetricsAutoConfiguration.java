package io.github.seonwkim.metrics.starter;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@AutoConfiguration
@ConditionalOnClass(name = "io.github.seonwkim.metrics.agent.MetricsAgent")
public class ActorMetricsAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ActorMetricsAutoConfiguration.class);
    private static final String ENV_TAG_PREFIX = "ACTOR_METRICS_TAG_";
    private static final String ENV_SAMPLING_RATE = "ACTOR_METRICS_SAMPLING_RATE";

    private final MeterRegistry meterRegistry;

    public ActorMetricsAutoConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        logger.info("Auto-configuring actor metrics...");

        MetricsConfiguration config = loadConfigFromEnvironment();

        MetricsRegistry registry = MetricsRegistry.builder()
                .meterRegistry(meterRegistry)
                .configuration(config)
                .build();

        MetricsAgent.setRegistry(registry);
        logger.info("Actor metrics auto-configured successfully");
    }

    private MetricsConfiguration loadConfigFromEnvironment() {
        MetricsConfiguration.Builder builder = MetricsConfiguration.builder();

        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_TAG_PREFIX)) {
                String tagName =
                        key.substring(ENV_TAG_PREFIX.length()).toLowerCase().replace('_', '.');
                builder.tag(tagName, value);
            }
        });

        String samplingRate = System.getenv(ENV_SAMPLING_RATE);
        if (samplingRate != null && !samplingRate.trim().isEmpty()) {
            try {
                double rate = Double.parseDouble(samplingRate);
                builder.sampling(MetricsConfiguration.SamplingConfig.rateBased(rate));
            } catch (NumberFormatException e) {
                logger.warn("Invalid sampling rate: {}", samplingRate);
            }
        }

        return builder.build();
    }
}
