package io.github.seonwkim.core.receptionist;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.SpringActorHandle;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.receptionist.ReceptionistTestActor.Command;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/** Local-mode tests for {@link SpringReceptionist}. */
@SpringBootTest(
        properties = {"spring.actor.pekko.name=receptionist-test", "spring.actor.pekko.actor.provider=local"})
class SpringReceptionistTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
        @Bean
        public ReceptionistTestActor localReceptionistTestActor() {
            return new ReceptionistTestActor();
        }

        @Bean
        public Registrar receptionistRegistrar(SpringReceptionist receptionist) {
            return new Registrar(receptionist);
        }

        @Bean
        public Finder receptionistFinder(SpringReceptionist receptionist) {
            return new Finder(receptionist);
        }
    }

    /** A Spring bean that registers actors. */
    static class Registrar {
        private final SpringReceptionist receptionist;

        Registrar(SpringReceptionist receptionist) {
            this.receptionist = receptionist;
        }

        void register(ServiceKey<Command> key, SpringActorHandle<Command> actor) {
            receptionist.register(key, actor);
        }
    }

    /** Another Spring bean that looks actors up. */
    static class Finder {
        private final SpringReceptionist receptionist;

        Finder(SpringReceptionist receptionist) {
            this.receptionist = receptionist;
        }

        Set<SpringActorHandle<Command>> find(ServiceKey<Command> key) throws Exception {
            return receptionist.find(key, Duration.ofSeconds(3)).toCompletableFuture().get();
        }
    }

    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private SpringReceptionist receptionist;

    @Autowired
    private Registrar registrar;

    @Autowired
    private Finder finder;

    private SpringActorHandle<Command> spawn(String id, BlockingQueue<String> received, ServiceKey<Command> selfKey) {
        return actorSystem
                .actor(ReceptionistTestActor.class)
                .withContext(new ReceptionistTestActor.Ctx(id, received, selfKey))
                .spawnAndWait();
    }

    private static Set<String> paths(Set<SpringActorHandle<Command>> handles) {
        return handles.stream().map(SpringActorHandle::getPath).collect(Collectors.toSet());
    }

    @Test
    void receptionistBeanIsCreated() {
        assertNotNull(receptionist);
    }

    @Test
    void beanRegistersAndAnotherBeanFinds() {
        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "register-find");
        SpringActorHandle<Command> actor = spawn("register-find-actor", new LinkedBlockingQueue<>(), null);

        registrar.register(key, actor);

        await().atMost(5, SECONDS).until(() -> paths(finder.find(key)).contains(actor.getPath()));
    }

    @Test
    void findReturnsHandlesThatReceiveTell() throws Exception {
        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "find-tell");
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        SpringActorHandle<Command> actor = spawn("find-tell-actor", received, null);
        receptionist.register(key, actor);

        await().atMost(5, SECONDS).until(() -> !finder.find(key).isEmpty());
        Set<SpringActorHandle<Command>> found = finder.find(key);
        for (SpringActorHandle<Command> handle : found) {
            assertEquals(actor.getPath(), handle.getPath());
            handle.tell(new ReceptionistTestActor.Ping("hello"));
        }

        assertEquals("hello", received.poll(5, SECONDS));
    }

    @Test
    void subscriberSeesRegistrationAndStop() throws Exception {
        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "subscribe-updates");
        BlockingQueue<Set<String>> updates = new LinkedBlockingQueue<>();
        SpringReceptionistSubscription<Command> subscription =
                receptionist.subscribe(key, set -> updates.add(paths(set)));
        try {
            SpringActorHandle<Command> actor = spawn("subscribe-updates-actor", new LinkedBlockingQueue<>(), null);
            receptionist.register(key, actor);

            awaitUpdate(updates, set -> set.contains(actor.getPath()));

            actor.tell(new ReceptionistTestActor.Stop());

            awaitUpdate(updates, set -> !set.contains(actor.getPath()));
        } finally {
            subscription.cancel();
        }
    }

    @Test
    void cancelledSubscriptionStopsHelperAndGetsNoMoreCallbacks() throws Exception {
        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "subscribe-cancel");
        BlockingQueue<Set<String>> cancelledUpdates = new LinkedBlockingQueue<>();
        SpringReceptionistSubscription<Command> subscription =
                receptionist.subscribe(key, set -> cancelledUpdates.add(paths(set)));

        // The initial (empty) listing arrives once the helper is subscribed.
        assertNotNull(cancelledUpdates.poll(5, SECONDS));

        subscription.cancel();
        assertTrue(subscription.isCancelled());
        subscription.whenTerminated().toCompletableFuture().get(5, SECONDS);
        cancelledUpdates.clear();

        // A second, live subscription proves the registration below was published.
        BlockingQueue<Set<String>> liveUpdates = new LinkedBlockingQueue<>();
        SpringReceptionistSubscription<Command> live = receptionist.subscribe(key, set -> liveUpdates.add(paths(set)));
        try {
            SpringActorHandle<Command> actor = spawn("subscribe-cancel-actor", new LinkedBlockingQueue<>(), null);
            receptionist.register(key, actor);
            awaitUpdate(liveUpdates, set -> set.contains(actor.getPath()));
        } finally {
            live.cancel();
        }

        assertTrue(cancelledUpdates.isEmpty(), "cancelled subscription must not be called");
    }

    @Test
    void actorRegistersItselfFromBehaviorSetup() throws Exception {
        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "self-register");
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        SpringActorHandle<Command> actor = spawn("self-register-actor", received, key);

        await().atMost(5, SECONDS).until(() -> paths(finder.find(key)).contains(actor.getPath()));

        finder.find(key).forEach(handle -> handle.tell(new ReceptionistTestActor.Ping("from-lookup")));
        assertEquals("from-lookup", received.poll(5, SECONDS));
    }

    private static void awaitUpdate(BlockingQueue<Set<String>> updates, java.util.function.Predicate<Set<String>> match)
            throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Set<String> next = updates.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (next != null && match.test(next)) {
                return;
            }
        }
        throw new AssertionError("expected listing update did not arrive within 5 seconds");
    }
}
