package io.github.seonwkim.actor.metrics.internal.hooks;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import javax.annotation.Nullable;

/**
 * The message meters of one tag set. The nullable ones are null while the agent records them.
 * {@code actor.message.processed} reads the processing timer's count, so it costs nothing per message.
 */
record MessageMeters(@Nullable Timer processingTime, Counter failures, @Nullable Counter restarts) {}
