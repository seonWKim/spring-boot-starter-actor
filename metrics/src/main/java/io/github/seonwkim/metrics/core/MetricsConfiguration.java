package io.github.seonwkim.metrics.core;

import java.util.*;

/**
 * Configuration for the metrics system.
 */
public class MetricsConfiguration {

    private boolean enabled = true;
    private Map<String, String> tags = new HashMap<>();
    private SamplingConfig sampling = new SamplingConfig();
    private Map<String, ModuleConfig> modules = new HashMap<>();

    public MetricsConfiguration() {}

    public static MetricsConfiguration getDefault() {
        return new MetricsConfiguration();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public SamplingConfig getSampling() {
        return sampling;
    }

    public Map<String, ModuleConfig> getModules() {
        return modules;
    }

    public static class Builder {
        private final MetricsConfiguration config = new MetricsConfiguration();

        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public Builder tag(String key, String value) {
            config.tags.put(key, value);
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            config.tags.putAll(tags);
            return this;
        }

        public Builder sampling(SamplingConfig sampling) {
            config.sampling = sampling;
            return this;
        }

        public Builder module(String moduleId, ModuleConfig moduleConfig) {
            config.modules.put(moduleId, moduleConfig);
            return this;
        }

        public MetricsConfiguration build() {
            return config;
        }
    }

    public static class SamplingConfig {
        private String strategy = "always"; // always, never, rate-based, adaptive
        private double rate = 1.0;
        private long targetThroughput = 1000;
        private double minRate = 0.01;
        private double maxRate = 1.0;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            this.rate = rate;
        }

        public long getTargetThroughput() {
            return targetThroughput;
        }

        public void setTargetThroughput(long targetThroughput) {
            this.targetThroughput = targetThroughput;
        }

        public double getMinRate() {
            return minRate;
        }

        public void setMinRate(double minRate) {
            this.minRate = minRate;
        }

        public double getMaxRate() {
            return maxRate;
        }

        public void setMaxRate(double maxRate) {
            this.maxRate = maxRate;
        }

        public static SamplingConfig adaptive(long targetThroughput, double minRate, double maxRate) {
            SamplingConfig config = new SamplingConfig();
            config.strategy = "adaptive";
            config.targetThroughput = targetThroughput;
            config.minRate = minRate;
            config.maxRate = maxRate;
            return config;
        }

        public static SamplingConfig rateBased(double rate) {
            SamplingConfig config = new SamplingConfig();
            config.strategy = "rate-based";
            config.rate = rate;
            return config;
        }

        public static SamplingConfig always() {
            SamplingConfig config = new SamplingConfig();
            config.strategy = "always";
            config.rate = 1.0;
            return config;
        }

        public static SamplingConfig never() {
            SamplingConfig config = new SamplingConfig();
            config.strategy = "never";
            config.rate = 0.0;
            return config;
        }
    }

    public static class ModuleConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public static ModuleConfig enabled() {
            ModuleConfig config = new ModuleConfig();
            config.enabled = true;
            return config;
        }

        public static ModuleConfig disabled() {
            ModuleConfig config = new ModuleConfig();
            config.enabled = false;
            return config;
        }
    }
}
