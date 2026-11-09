package io.github.seonwkim.actor;

import io.github.seonwkim.actor.test.ActorCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A 3-node cluster for each test, on {@link ActorCluster} (the published helper in actor-test).
 * Subclasses give the application class and may give roles and properties; tests reach the nodes
 * through {@link #context1}, {@link #context2} and {@link #context3}.
 */
public abstract class AbstractClusterTest {

    protected ConfigurableApplicationContext context1;
    protected ConfigurableApplicationContext context2;
    protected ConfigurableApplicationContext context3;

    private ActorCluster cluster;

    /** The application every node runs. */
    protected abstract Class<?> getApplicationClass();

    protected String getActorSystemName() {
        return "spring-pekko-example";
    }

    protected String[] getAdditionalProperties() {
        return new String[0];
    }

    protected String[] getNode1Roles() {
        return new String[0];
    }

    protected String[] getNode2Roles() {
        return new String[0];
    }

    protected String[] getNode3Roles() {
        return new String[0];
    }

    @BeforeEach
    void setUpCluster() {
        cluster = ActorCluster.builder(getApplicationClass())
                .nodes(3)
                .systemName(getActorSystemName())
                .properties(
                        "spring.main.allow-bean-definition-overriding=true",
                        "spring.actor.pekko.actor.allow-java-serialization=off",
                        "spring.actor.pekko.actor.warn-about-java-serializer-usage=on")
                .properties(getAdditionalProperties())
                .roles(1, getNode1Roles())
                .roles(2, getNode2Roles())
                .roles(3, getNode3Roles())
                .start();
        context1 = cluster.node(1);
        context2 = cluster.node(2);
        context3 = cluster.node(3);
    }

    @AfterEach
    void tearDownCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    /** Waits until all 3 nodes are up. */
    protected void waitUntilClusterInitialized() {
        cluster.awaitMembersUp(3);
    }

    /** Waits until the running nodes see this many members up, for tests that stop nodes. */
    protected void waitUntilClusterHasMembers(int expectedNodeCount) {
        cluster.awaitMembersUp(expectedNodeCount);
    }
}
