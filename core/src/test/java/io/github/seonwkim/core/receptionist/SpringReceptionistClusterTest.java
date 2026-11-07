package io.github.seonwkim.core.receptionist;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.seonwkim.core.AbstractClusterTest;
import io.github.seonwkim.core.SpringActorHandle;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.receptionist.ReceptionistTestActor.Command;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Cluster-mode tests for {@link SpringReceptionist}. Listings replicate across nodes eventually,
 * so lookups on other nodes are polled with a bounded wait.
 */
class SpringReceptionistClusterTest extends AbstractClusterTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
        @Bean
        public ReceptionistTestActor receptionistTestActor() {
            return new ReceptionistTestActor();
        }
    }

    @Override
    protected Class<?> getApplicationClass() {
        return TestApp.class;
    }

    @Test
    void actorRegisteredOnNode1IsFoundOnNode2() throws Exception {
        waitUntilClusterInitialized();

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringReceptionist receptionist1 = context1.getBean(SpringReceptionist.class);
        SpringReceptionist receptionist2 = context2.getBean(SpringReceptionist.class);

        ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "cluster-find");
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        SpringActorHandle<Command> actor = system1.actor(ReceptionistTestActor.class)
                .withContext(new ReceptionistTestActor.Ctx("cluster-find-actor", received, null))
                .spawnAndWait();
        receptionist1.register(key, actor);

        AtomicReference<Set<SpringActorHandle<Command>>> found = new AtomicReference<>();
        await().atMost(15, SECONDS).pollInterval(200, MILLISECONDS).until(() -> {
            Set<SpringActorHandle<Command>> current =
                    receptionist2.find(key, Duration.ofSeconds(3)).toCompletableFuture().get();
            found.set(current);
            return !current.isEmpty();
        });

        assertEquals(1, found.get().size());
        found.get().iterator().next().tell(new ReceptionistTestActor.Ping("from-node-2"));
        assertEquals("from-node-2", received.poll(10, SECONDS));
    }
}
