package org.github.seonwkim.core.behavior;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.cluster.ClusterEvent.MemberLeft;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.github.seonwkim.core.behavior.ClusterEventBehavior.ClusterDomainWrappedEvent;
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

    private static final int[] httpPorts = { 30001, 30002, 30003 };
    private static final int[] arteryPorts = { 40001, 40002, 40003 };

    @SpringBootApplication(scanBasePackages = "org.github.seonwkim.core")
    public static class ClusterTestApp {
        @Bean
        public ClusterEventCollector clusterEventCollector() {
            return new ClusterEventCollector();
        }
    }

    public static class ClusterEventCollector {
        private final List<Object> events = new CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        public void onMemberUp(ClusterDomainWrappedEvent event) {
            System.out.println("SOME EVENT: " + event);
            events.add(event.getEvent());
        }

        public int eventCount(Class<?> eventType) {
            return (int) events.stream().filter(event -> event.getClass() == eventType).count();
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
        ClusterEventCollector collector = context1.getBean(ClusterEventCollector.class);

        await().atMost(10, SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .until(() -> collector.eventCount(MemberUp.class) >= 3);
        assertTrue(collector.eventCount(MemberUp.class) >= 3, "Expected at least 3 MemberUp events");

        context2.close();
        context3.close();
        await().atMost(10, SECONDS)
               .pollInterval(200, TimeUnit.MILLISECONDS)
               .until(() -> collector.eventCount(MemberLeft.class) >= 2);
        assertTrue(collector.eventCount(MemberLeft.class) >= 2, "Expected at least 3 MemberLeft events");
    }

    @AfterEach
    void tearDown() {
        if (context1.isActive()) {
            context1.close();
        }
        if (context2.isActive()) {
            context2.close();
        }
        if (context3.isActive()) {
            context3.close();
        }
    }
}
