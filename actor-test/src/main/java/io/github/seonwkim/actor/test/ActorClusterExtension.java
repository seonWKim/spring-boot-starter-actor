package io.github.seonwkim.actor.test;

import javax.annotation.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts an {@link ActorCluster} for JUnit 5 tests. On a static field it starts once for the test
 * class; on an instance field, once for each test. Either way every node is up before the test runs.
 *
 * <pre>{@code
 * @RegisterExtension
 * final ActorClusterExtension cluster = new ActorClusterExtension(ActorCluster.builder(MyApp.class).nodes(3));
 *
 * @Test
 * void greetsOnEveryNode() {
 *     SpringActorSystem third = cluster.node(3).getBean(SpringActorSystem.class);
 * }
 * }</pre>
 */
public final class ActorClusterExtension
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private final ActorCluster.Builder builder;
    private boolean perClass;

    @Nullable private ActorCluster cluster;

    public ActorClusterExtension(ActorCluster.Builder builder) {
        this.builder = builder;
    }

    /** The running cluster. */
    public ActorCluster cluster() {
        if (cluster == null) {
            throw new IllegalStateException("The cluster runs only during a test");
        }
        return cluster;
    }

    /** The context of a node, numbered from 1. */
    public ConfigurableApplicationContext node(int number) {
        return cluster().node(number);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        perClass = true;
        cluster = builder.start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!perClass) {
            cluster = builder.start();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (!perClass) {
            stop();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stop();
    }

    private void stop() {
        if (cluster != null) {
            ActorCluster stopping = cluster;
            cluster = null;
            stopping.close();
        }
    }
}
