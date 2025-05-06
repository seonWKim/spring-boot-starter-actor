package org.github.seonwkim.core.behavior;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

public class ClusterEventBehaviorTest {

    private static ConfigurableApplicationContext context1;
    private static ConfigurableApplicationContext context2;
    private static ConfigurableApplicationContext context3;

    private static final int[] httpPorts = { 31433, 35975, 37325 };
    private static final int[] arteryPorts = { 13201, 24382, 28387 };

    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    public static class ClusterTestApp {
        @Bean
        public ClusterEventCollector clusterEventCollector() {
            return new ClusterEventCollector();
        }
    }

    public static class ClusterEventCollector {
        private final List<Object> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        public void onMemberUp(ClusterEventBehavior.MemberUpEvent event) {
            events.add(event);
        }

        @org.springframework.context.event.EventListener
        public void onMemberRemoved(ClusterEventBehavior.MemberRemovedEvent event) {
            events.add(event);
        }

        public boolean hasEvent(Class<?> type) {
            return events.stream().anyMatch(type::isInstance);
        }

        public int eventCount(Class<?> type) {
            return (int) events.stream().filter(type::isInstance).count();
        }
    }

    @BeforeEach
    void setUp() {
        String seedNodes = String.format(
                "pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d",
                arteryPorts[0], arteryPorts[1], arteryPorts[2]);

        context1 = startContext(httpPorts[0], arteryPorts[0], seedNodes, "node1");
        context2 = startContext(httpPorts[1], arteryPorts[1], seedNodes, "node2");
        context3 = startContext(httpPorts[2], arteryPorts[2], seedNodes, "node3");
    }

    private static ConfigurableApplicationContext startContext(
            int httpPort,
            int arteryPort,
            String seedNodes,
            String nodeId
    ) {
        return new SpringApplicationBuilder(ClusterTestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "server.port=" + httpPort,
                        "actor.pekko.enabled=true",
                        "actor.pekko.name=spring-pekko-example",
                        "actor.pekko.actor.provider=cluster",
                        "actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                        "actor.pekko.remote.artery.canonical.port=" + arteryPort,
                        "actor.pekko.cluster.name=cluster",
                        "actor.pekko.cluster.seed-nodes=" + seedNodes,
                        "actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
                )
                .run();
    }

    @Test
    void clusterEventsShouldBePublished() {
        ClusterEventCollector collector1 = context1.getBean(ClusterEventCollector.class);

        await().atMost(10, SECONDS)
               .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
               .until(() -> collector1.eventCount(ClusterEventBehavior.MemberUpEvent.class) >= 3);

        assertTrue(collector1.eventCount(ClusterEventBehavior.MemberUpEvent.class) >= 3,
                   "Expected at least 3 MemberUp events");
    }

    @AfterEach
    void tearDown() {
        context1.close();
        context2.close();
        context3.close();
    }
}
