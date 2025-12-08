package io.github.seonwkim.metrics.api;

import io.github.seonwkim.metrics.core.MetricsRegistry;

/**
 * Interface for instrumentation modules that provide bytecode transformations.
 */
public interface InstrumentationModule {

    String moduleId();

    void initialize(MetricsRegistry registry);

    default void shutdown() {}
}
