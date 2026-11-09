package io.github.seonwkim.actor.metrics.internal.hooks;

import com.typesafe.config.Config;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import org.apache.pekko.actor.ActorPath;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.AbstractNodeQueue;
import org.apache.pekko.dispatch.Envelope;
import org.apache.pekko.dispatch.MailboxType;
import org.apache.pekko.dispatch.MessageQueue;
import org.apache.pekko.dispatch.ProducesMessageQueue;
import org.apache.pekko.dispatch.UnboundedMessageQueueSemantics;
import scala.Option;

/**
 * Typed actors' default unbounded mailbox, plus mailbox size, time in mailbox and running actors.
 * Pekko creates it by name ({@code pekko.actor.typed.default-mailbox.mailbox-type}, set by {@link ActorMeters});
 * it finds its meters in the actor system's setup. A mailbox counts nothing until its actor's
 * instrumented behavior starts and binds it to the actor's tags; system actors are never bound.
 */
public final class InstrumentedMailbox implements MailboxType, ProducesMessageQueue<InstrumentedMailbox.Queue> {

    @Nullable private final ActorMeters meters;

    public InstrumentedMailbox(ActorSystem.Settings settings, Config config) {
        this.meters = settings.setup().get(ActorMeters.class).orElse(null);
    }

    @Override
    public MessageQueue create(Option<ActorRef> owner, Option<ActorSystem> system) {
        Queue queue = new Queue();
        if (meters != null && owner.isDefined()) {
            queue.register(meters, owner.get().path());
        }
        return queue;
    }

    /** The meters mailboxes of one tag set share. */
    record Meters(LongAdder size, LongAdder running, Timer time) {}

    private record Entry(Envelope envelope, long enqueuedAt, @Nullable Meters meters) {}

    /**
     * Pekko's default typed queue (an unbounded multi-producer, single-consumer node queue) that counts
     * what it holds once bound.
     */
    public static final class Queue extends AbstractNodeQueue<Entry>
            implements MessageQueue, UnboundedMessageQueueSemantics {
        private final AtomicReference<Meters> bound = new AtomicReference<>();

        @Nullable private ActorMeters owner;

        @Nullable private ActorPath path;

        void register(ActorMeters owner, ActorPath path) {
            this.owner = owner;
            this.path = path;
            owner.mailboxCreated(path, this);
        }

        /** Starts counting into {@code meters}; only the first call counts (a restart calls again). */
        void bind(Meters meters) {
            if (bound.compareAndSet(null, meters)) {
                meters.running().increment();
            }
        }

        @Override
        public void enqueue(ActorRef receiver, Envelope handle) {
            Meters meters = bound.get();
            if (meters == null) {
                add(new Entry(handle, 0, null));
                return;
            }
            meters.size().increment();
            add(new Entry(handle, System.nanoTime(), meters));
        }

        @Override
        @Nullable public Envelope dequeue() {
            Entry entry = poll();
            if (entry == null) {
                return null;
            }
            // Each entry is uncounted in the meters it was counted in.
            Meters meters = entry.meters();
            if (meters != null) {
                meters.size().decrement();
                meters.time().record(System.nanoTime() - entry.enqueuedAt(), TimeUnit.NANOSECONDS);
            }
            return entry.envelope();
        }

        @Override
        public int numberOfMessages() {
            return count();
        }

        @Override
        public boolean hasMessages() {
            return !isEmpty();
        }

        @Override
        public void cleanUp(ActorRef owner, MessageQueue deadLetters) {
            Entry entry;
            while ((entry = poll()) != null) {
                Meters counted = entry.meters();
                if (counted != null) {
                    counted.size().decrement();
                }
                deadLetters.enqueue(owner, entry.envelope());
            }
            Meters meters = bound.getAndSet(null);
            if (meters != null) {
                meters.running().decrement();
            }
            ActorMeters registeredWith = this.owner;
            ActorPath registeredAt = path;
            if (registeredWith != null && registeredAt != null) {
                registeredWith.mailboxClosed(registeredAt, this);
            }
        }
    }
}
