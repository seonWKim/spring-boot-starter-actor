package io.github.seonwkim.metrics.core;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared mutable state for instrumentation modules.
 *
 * <p>All per-module state (counters, gauges, thread-locals) is held here instead of in
 * static fields on individual modules. This makes modules stateless, simplifies testing
 * (one context per registry), and avoids global state leaks across test runs.
 *
 * <p>Owned by {@link MetricsRegistry}; advice code accesses it via
 * {@code MetricsAgent.getRegistry().getContext()}.
 */
public final class MetricsContext {

    // ── Actor lifecycle ──────────────────────────────────────────────────
    private final AtomicLong activeActorCount = new AtomicLong(0);

    public AtomicLong getActiveActorCount() {
        return activeActorCount;
    }

    // ── Stash ────────────────────────────────────────────────────────────
    /** Thread-local actor class for stash operations (set during ActorCell.invoke). */
    private final ThreadLocal<String> currentActorClass = new ThreadLocal<>();

    /** Stashed count per actor class (aggregate across instances). */
    private final ConcurrentHashMap<String, AtomicLong> stashSizes = new ConcurrentHashMap<>();

    public ThreadLocal<String> getCurrentActorClass() {
        return currentActorClass;
    }

    public ConcurrentHashMap<String, AtomicLong> getStashSizes() {
        return stashSizes;
    }

    // ── Mailbox ──────────────────────────────────────────────────────────
    /** WeakHashMap to store envelope timestamps without preventing GC. */
    private final Map<Object, Long> envelopeTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

    /** Aggregated mailbox size per actor class (not per instance, to avoid high cardinality). */
    private final ConcurrentHashMap<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

    /** Peak mailbox size per actor class. */
    private final ConcurrentHashMap<String, AtomicLong> mailboxSizesMax = new ConcurrentHashMap<>();

    public Map<Object, Long> getEnvelopeTimestamps() {
        return envelopeTimestamps;
    }

    public ConcurrentHashMap<String, AtomicLong> getMailboxSizes() {
        return mailboxSizes;
    }

    public ConcurrentHashMap<String, AtomicLong> getMailboxSizesMax() {
        return mailboxSizesMax;
    }

    // ── Message processing ───────────────────────────────────────────────
    /** ThreadLocal to pass message type from onEnter to onExit. */
    private final ThreadLocal<String> currentMessageType = new ThreadLocal<>();

    public ThreadLocal<String> getCurrentMessageType() {
        return currentMessageType;
    }

    // ── Cleanup ──────────────────────────────────────────────────────────
    /**
     * Clear all state. Called during registry shutdown.
     * ThreadLocals are only cleared for the calling thread;
     * values on other threads will be GC'd when those threads terminate.
     */
    public void clear() {
        activeActorCount.set(0);
        currentActorClass.remove();
        stashSizes.clear();
        envelopeTimestamps.clear();
        mailboxSizes.clear();
        mailboxSizesMax.clear();
        currentMessageType.remove();
    }
}
