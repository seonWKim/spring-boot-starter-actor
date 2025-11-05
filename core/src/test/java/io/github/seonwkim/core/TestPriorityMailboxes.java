package io.github.seonwkim.core;

import com.typesafe.config.Config;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.*;
import scala.concurrent.duration.Duration;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom priority mailbox implementations for testing.
 * Priority mailboxes require extending the mailbox class and providing a Comparator.
 *
 * <p>This class also provides tracking/interceptor functionality to verify
 * that mailboxes are constructed with correct configurations during tests.
 */
public class TestPriorityMailboxes {

    /**
     * Tracks mailbox construction events for testing.
     */
    public static class ConstructionTracker {
        private static final ConcurrentHashMap<String, AtomicInteger> constructionCounts = new ConcurrentHashMap<>();
        private static final ConcurrentHashMap<String, Integer> lastCapacity = new ConcurrentHashMap<>();

        public static void recordConstruction(String mailboxType) {
            constructionCounts.computeIfAbsent(mailboxType, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public static void recordConstruction(String mailboxType, int capacity) {
            recordConstruction(mailboxType);
            lastCapacity.put(mailboxType, capacity);
        }

        public static int getConstructionCount(String mailboxType) {
            AtomicInteger count = constructionCounts.get(mailboxType);
            return count != null ? count.get() : 0;
        }

        public static Integer getLastCapacity(String mailboxType) {
            return lastCapacity.get(mailboxType);
        }

        public static void reset() {
            constructionCounts.clear();
            lastCapacity.clear();
        }
    }

    /**
     * Comparator for priority mailboxes.
     * Compares envelopes based on message priority.
     * Lower comparison result = higher priority.
     */
    private static final Comparator<Envelope> PRIORITY_COMPARATOR = (e1, e2) -> {
        Object m1 = e1.message();
        Object m2 = e2.message();

        // Ping messages get higher priority (return negative for higher priority)
        boolean m1IsPing = m1 instanceof MailboxConfigIntegrationTest.Ping;
        boolean m2IsPing = m2 instanceof MailboxConfigIntegrationTest.Ping;

        if (m1IsPing && !m2IsPing) {
            return -1; // m1 has higher priority
        } else if (!m1IsPing && m2IsPing) {
            return 1; // m2 has higher priority
        }
        return 0; // Same priority
    };

    /**
     * Custom UnboundedPriorityMailbox for testing.
     * Messages of type Ping get higher priority.
     */
    public static class TestUnboundedPriorityMailbox extends UnboundedPriorityMailbox {
        public TestUnboundedPriorityMailbox(ActorSystem.Settings settings, Config config) {
            super(PRIORITY_COMPARATOR);
            ConstructionTracker.recordConstruction("UnboundedPriorityMailbox");
        }
    }

    /**
     * Custom UnboundedStablePriorityMailbox for testing.
     * Stable priority maintains insertion order for messages with the same priority.
     */
    public static class TestUnboundedStablePriorityMailbox extends UnboundedStablePriorityMailbox {
        public TestUnboundedStablePriorityMailbox(ActorSystem.Settings settings, Config config) {
            super(PRIORITY_COMPARATOR);
            ConstructionTracker.recordConstruction("UnboundedStablePriorityMailbox");
        }
    }

    /**
     * Custom BoundedPriorityMailbox for testing.
     * Bounded priority mailbox with capacity limit.
     */
    public static class TestBoundedPriorityMailbox extends BoundedPriorityMailbox {
        public TestBoundedPriorityMailbox(ActorSystem.Settings settings, Config config) {
            super(PRIORITY_COMPARATOR, config.getInt("mailbox-capacity"), Duration.create(10, TimeUnit.SECONDS));
            ConstructionTracker.recordConstruction("BoundedPriorityMailbox", config.getInt("mailbox-capacity"));
        }
    }

    /**
     * Custom BoundedStablePriorityMailbox for testing.
     * Bounded stable priority mailbox with capacity limit.
     */
    public static class TestBoundedStablePriorityMailbox extends BoundedStablePriorityMailbox {
        public TestBoundedStablePriorityMailbox(ActorSystem.Settings settings, Config config) {
            super(PRIORITY_COMPARATOR, config.getInt("mailbox-capacity"), Duration.create(10, TimeUnit.SECONDS));
            ConstructionTracker.recordConstruction("BoundedStablePriorityMailbox", config.getInt("mailbox-capacity"));
        }
    }
}
