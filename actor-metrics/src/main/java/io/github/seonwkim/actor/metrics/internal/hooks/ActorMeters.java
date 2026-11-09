package io.github.seonwkim.actor.metrics.internal.hooks;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import io.github.seonwkim.actor.internal.ActorInstrumentation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToDoubleFunction;
import org.apache.pekko.actor.ActorPath;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.dispatch.MessageDispatcher;

/**
 * Actor meters from hooks the starter owns, without the Java agent: a behavior interceptor, an
 * instrumented default mailbox and the default dispatchers' executors. Every tag is bounded by the
 * application's code (actor classes, dispatchers, systems), so the series count does not grow with
 * the number of actors. {@code actor.path} is the exception: it is off by default and capped.
 *
 * <p>When the agent is attached it already records the message, mailbox and lifecycle meters under
 * the same names; then only the meters it lacks ({@link #FAILURES}, the dispatcher meters) are
 * registered here, so no meter is registered twice.
 */
public final class ActorMeters extends Setup implements ActorInstrumentation {

    public static final String PROCESSED = "actor.message.processed";
    public static final String PROCESSING_TIME = "actor.message.processing.time";
    public static final String FAILURES = "actor.message.failures";
    public static final String MAILBOX_SIZE = "actor.mailbox.size";
    public static final String MAILBOX_TIME = "actor.mailbox.time";
    public static final String ACTIVE = "actor.lifecycle.active";
    public static final String RESTARTS = "actor.lifecycle.restarts";
    public static final String DISPATCHER_THREADS = "actor.dispatcher.threads";
    public static final String DISPATCHER_ACTIVE_THREADS = "actor.dispatcher.threads.active";
    public static final String DISPATCHER_QUEUED = "actor.dispatcher.queued";

    /** The {@code actor.path} value of every actor past the cap. */
    public static final String OTHER_PATH = "other";

    /** Typed actors' default mailbox; classic actors (Pekko's own) keep theirs. */
    static final String DEFAULT_MAILBOX_TYPE = "pekko.actor.typed.default-mailbox.mailbox-type";

    static final String PEKKO_DEFAULT_MAILBOX = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox";
    static final List<String> DISPATCHERS =
            List.of("pekko.actor.default-dispatcher", "pekko.actor.default-blocking-io-dispatcher");

    private final MeterRegistry registry;
    private final boolean agentAttached;
    private final int pathTagLimit;

    private final Map<List<String>, MessageMeters> messageMeters = new ConcurrentHashMap<>();
    private final Map<List<String>, InstrumentedMailbox.Meters> mailboxMeters = new ConcurrentHashMap<>();
    private final Map<List<String>, AtomicReference<ExecutorService>> executors = new ConcurrentHashMap<>();
    /** Mailboxes of actors that have not started yet; an actor's first start binds its mailbox. */
    private final Map<ActorPath, InstrumentedMailbox.Queue> unboundMailboxes = new ConcurrentHashMap<>();

    private final Set<String> taggedPaths = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pathSlots = new AtomicInteger();

    /**
     * @param agentAttached whether the metrics agent records the message, mailbox and lifecycle meters
     * @param pathTagLimit how many actor paths get their own {@code actor.path} tag; 0 adds no such tag
     */
    public ActorMeters(MeterRegistry registry, boolean agentAttached, int pathTagLimit) {
        if (pathTagLimit < 0) {
            throw new IllegalArgumentException("pathTagLimit must be 0 or more, got " + pathTagLimit);
        }
        this.registry = registry;
        this.agentAttached = agentAttached;
        this.pathTagLimit = pathTagLimit;
    }

    @Override
    public Config configure(Config config) {
        Config result = config;
        // The agent counts mailboxes itself; and a mailbox the application chose stays as it is.
        if (!agentAttached && PEKKO_DEFAULT_MAILBOX.equals(config.getString(DEFAULT_MAILBOX_TYPE))) {
            result = result.withValue(
                    DEFAULT_MAILBOX_TYPE, ConfigValueFactory.fromAnyRef(InstrumentedMailbox.class.getName()));
        }
        for (String dispatcher : DISPATCHERS) {
            result = InstrumentedExecutor.wrap(result, dispatcher);
        }
        return result;
    }

    @Override
    public ActorSystemSetup setup(ActorSystemSetup setup) {
        return setup.withSetup(this);
    }

    @Override
    public <T> Behavior<T> instrument(Class<?> actorClass, Behavior<T> behavior) {
        return Behaviors.setup(ctx -> {
            String dispatcher = dispatcherOf(ctx);
            String system = ctx.getSystem().name();
            Tags tags = Tags.of("actor.class", actorClass.getSimpleName(), "dispatcher", dispatcher, "system", system);
            if (!agentAttached) {
                InstrumentedMailbox.Queue mailbox =
                        unboundMailboxes.remove(ctx.getSelf().path());
                if (mailbox != null) {
                    mailbox.bind(mailboxMeters(tags));
                }
            }
            MessageMeters meters = messageMeters(pathTagLimit == 0 ? tags : tags.and("actor.path", pathTag(ctx)));
            return MessageInterceptor.intercept(meters, behavior);
        });
    }

    private static String dispatcherOf(ActorContext<?> ctx) {
        return ctx.getExecutionContext() instanceof MessageDispatcher dispatcher ? dispatcher.id() : "unknown";
    }

    private String pathTag(ActorContext<?> ctx) {
        String path = ctx.getSelf().path().toStringWithoutAddress();
        if (taggedPaths.contains(path)) {
            return path;
        }
        if (pathSlots.getAndIncrement() < pathTagLimit) {
            taggedPaths.add(path);
            return path;
        }
        return OTHER_PATH;
    }

    private MessageMeters messageMeters(Tags tags) {
        return messageMeters.computeIfAbsent(key(tags), key -> {
            Timer processingTime = null;
            if (!agentAttached) {
                processingTime = Timer.builder(PROCESSING_TIME)
                        .description("Time actors spent processing a message")
                        .tags(tags)
                        .register(registry);
                FunctionCounter.builder(PROCESSED, processingTime, Timer::count)
                        .description("Messages actors processed")
                        .tags(tags)
                        .register(registry);
            }
            Counter failures = Counter.builder(FAILURES)
                    .description("Messages whose processing threw")
                    .tags(tags)
                    .register(registry);
            Counter restarts = agentAttached
                    ? null
                    : Counter.builder(RESTARTS)
                            .description("Actor restarts by supervision")
                            .tags(tags)
                            .register(registry);
            return new MessageMeters(processingTime, failures, restarts);
        });
    }

    private InstrumentedMailbox.Meters mailboxMeters(Tags tags) {
        return mailboxMeters.computeIfAbsent(key(tags), key -> {
            LongAdder size = new LongAdder();
            LongAdder active = new LongAdder();
            Gauge.builder(MAILBOX_SIZE, size, LongAdder::sum)
                    .description("Messages waiting in actors' mailboxes")
                    .tags(tags)
                    .strongReference(true)
                    .register(registry);
            Gauge.builder(ACTIVE, active, LongAdder::sum)
                    .description("Actors running")
                    .tags(tags)
                    .strongReference(true)
                    .register(registry);
            Timer time = Timer.builder(MAILBOX_TIME)
                    .description("Time a message waited in the mailbox")
                    .tags(tags)
                    .register(registry);
            return new InstrumentedMailbox.Meters(size, active, time);
        });
    }

    private static List<String> key(Tags tags) {
        return tags.stream().map(tag -> tag.getKey() + "=" + tag.getValue()).toList();
    }

    /** A new default mailbox of the actor at {@code owner}; bound to its meters when the actor starts. */
    void mailboxCreated(ActorPath owner, InstrumentedMailbox.Queue queue) {
        unboundMailboxes.put(owner, queue);
    }

    void mailboxClosed(ActorPath owner, InstrumentedMailbox.Queue queue) {
        unboundMailboxes.remove(owner, queue);
    }

    /** A dispatcher (re)created its executor; its gauges read the latest one. */
    void executorStarted(String system, String dispatcher, ExecutorService executor) {
        Tags tags = Tags.of("dispatcher", dispatcher, "system", system);
        executors
                .computeIfAbsent(key(tags), key -> {
                    AtomicReference<ExecutorService> current = new AtomicReference<>();
                    gauge(DISPATCHER_THREADS, "Threads in the dispatcher's pool", tags, current, ActorMeters::threads);
                    gauge(
                            DISPATCHER_ACTIVE_THREADS,
                            "Threads of the dispatcher's pool running tasks",
                            tags,
                            current,
                            ActorMeters::activeThreads);
                    gauge(DISPATCHER_QUEUED, "Tasks waiting for a thread", tags, current, ActorMeters::queued);
                    return current;
                })
                .set(executor);
    }

    private void gauge(
            String name,
            String description,
            Tags tags,
            AtomicReference<ExecutorService> executor,
            ToDoubleFunction<ExecutorService> value) {
        Gauge.builder(name, executor, ref -> {
                    ExecutorService current = ref.get();
                    return current == null ? Double.NaN : value.applyAsDouble(current);
                })
                .description(description)
                .tags(tags)
                .strongReference(true)
                .register(registry);
    }

    private static double threads(ExecutorService executor) {
        if (executor instanceof ForkJoinPool pool) {
            return pool.getPoolSize();
        }
        return executor instanceof ThreadPoolExecutor pool ? pool.getPoolSize() : Double.NaN;
    }

    private static double activeThreads(ExecutorService executor) {
        if (executor instanceof ForkJoinPool pool) {
            return pool.getActiveThreadCount();
        }
        return executor instanceof ThreadPoolExecutor pool ? pool.getActiveCount() : Double.NaN;
    }

    private static double queued(ExecutorService executor) {
        if (executor instanceof ForkJoinPool pool) {
            return pool.getQueuedSubmissionCount() + pool.getQueuedTaskCount();
        }
        return executor instanceof ThreadPoolExecutor pool ? pool.getQueue().size() : Double.NaN;
    }
}
