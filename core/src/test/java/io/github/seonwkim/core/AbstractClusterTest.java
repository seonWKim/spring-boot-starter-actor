package io.github.seonwkim.core;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Abstract base class for cluster tests that provides common cluster initialization and teardown logic.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Starting a 3-node cluster with unique ports for each test</li>
 *   <li>Waiting for the cluster to be fully initialized</li>
 *   <li>Proper cleanup and shutdown of all nodes</li>
 *   <li>Port management to avoid conflicts between tests</li>
 * </ul>
 *
 * <p>Subclasses should:
 * <ol>
 *   <li>Define their test application class via {@link #getApplicationClass()}</li>
 *   <li>Access cluster nodes via {@link #context1}, {@link #context2}, {@link #context3}</li>
 *   <li>Call {@link #waitUntilClusterInitialized()} before running cluster operations</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>
 * public class MyClusterTest extends AbstractClusterTest {
 *
 *     {@literal @}Override
 *     protected Class<?> getApplicationClass() {
 *         return MyTestApp.class;
 *     }
 *
 *     {@literal @}Test
 *     void myTest() {
 *         waitUntilClusterInitialized();
 *
 *         SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
 *         // ... test logic
 *     }
 * }
 * </pre>
 */
public abstract class AbstractClusterTest {

    /** First node in the cluster */
    protected ConfigurableApplicationContext context1;

    /** Second node in the cluster */
    protected ConfigurableApplicationContext context2;

    /** Third node in the cluster */
    protected ConfigurableApplicationContext context3;

    /**
     * Returns the Spring Boot application class to use for the cluster nodes.
     * Subclasses must implement this to specify their test application.
     *
     * @return The application class (typically annotated with {@code @SpringBootApplication})
     */
    protected abstract Class<?> getApplicationClass();

    /**
     * Returns the Pekko actor system name to use for the cluster.
     * Default is "spring-pekko-example". Override if a different name is needed.
     *
     * @return The actor system name
     */
    protected String getActorSystemName() {
        return "spring-pekko-example";
    }

    /**
     * Returns additional properties to apply to the cluster nodes.
     * Override this method to add custom configuration for specific tests.
     *
     * @return Array of additional property strings, or empty array
     */
    protected String[] getAdditionalProperties() {
        return new String[0];
    }

    /**
     * Returns the cluster roles for node 1.
     * Override this method to assign specific roles to node 1.
     *
     * @return Array of role names, or empty array for no roles
     */
    protected String[] getNode1Roles() {
        return new String[0];
    }

    /**
     * Returns the cluster roles for node 2.
     * Override this method to assign specific roles to node 2.
     *
     * @return Array of role names, or empty array for no roles
     */
    protected String[] getNode2Roles() {
        return new String[0];
    }

    /**
     * Returns the cluster roles for node 3.
     * Override this method to assign specific roles to node 3.
     *
     * @return Array of role names, or empty array for no roles
     */
    protected String[] getNode3Roles() {
        return new String[0];
    }

    /**
     * Sets up a 3-node cluster before each test.
     * Automatically finds available ports to avoid conflicts.
     */
    @BeforeEach
    void setUpCluster() {
        // Find available ports dynamically to avoid conflicts
        final int[] httpPorts = findAvailablePorts(3);
        final int[] arteryPorts = findAvailablePorts(3);

        String seedNodes = String.format(
                "pekko://%s@127.0.0.1:%d,pekko://%s@127.0.0.1:%d,pekko://%s@127.0.0.1:%d",
                getActorSystemName(),
                arteryPorts[0],
                getActorSystemName(),
                arteryPorts[1],
                getActorSystemName(),
                arteryPorts[2]);

        context1 = startContext(httpPorts[0], arteryPorts[0], seedNodes, getNode1Roles());
        context2 = startContext(httpPorts[1], arteryPorts[1], seedNodes, getNode2Roles());
        context3 = startContext(httpPorts[2], arteryPorts[2], seedNodes, getNode3Roles());
    }

    /**
     * Tears down the cluster after each test.
     * Ensures all nodes are properly closed.
     */
    @AfterEach
    void tearDownCluster() {
        System.out.println("Cluster shutting down");
        if (context1 != null && context1.isActive()) {
            context1.close();
        }
        if (context2 != null && context2.isActive()) {
            context2.close();
        }
        if (context3 != null && context3.isActive()) {
            context3.close();
        }
    }

    /**
     * Starts a single cluster node with the specified configuration.
     *
     * @param httpPort The HTTP port for this node
     * @param arteryPort The Pekko remoting (Artery) port for this node
     * @param seedNodes The comma-separated list of seed nodes
     * @param roles The cluster roles for this node
     * @return The Spring application context for this node
     */
    private ConfigurableApplicationContext startContext(int httpPort, int arteryPort, String seedNodes, String[] roles) {
        // Base properties required for cluster mode
        String[] baseProperties = {
            "server.port=" + httpPort,
            "spring.main.allow-bean-definition-overriding=true",
            "spring.actor.pekko.name=" + getActorSystemName(),
            "spring.actor.pekko.actor.provider=cluster",
            "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
            "spring.actor.pekko.remote.artery.canonical.port=" + arteryPort,
            "spring.actor.pekko.cluster.name=cluster",
            "spring.actor.pekko.cluster.seed-nodes=" + seedNodes,
            "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
            "spring.actor.pekko.actor.allow-java-serialization=off",
            "spring.actor.pekko.actor.warn-about-java-serializer-usage=on"
        };

        // Merge base properties, roles, and additional properties
        String[] additionalProps = getAdditionalProperties();
        int totalSize = baseProperties.length + roles.length + additionalProps.length;
        String[] allProperties = new String[totalSize];

        System.arraycopy(baseProperties, 0, allProperties, 0, baseProperties.length);

        // Add role properties as array elements (Pekko expects a list)
        int offset = baseProperties.length;
        for (int i = 0; i < roles.length; i++) {
            allProperties[offset + i] = "spring.actor.pekko.cluster.roles[" + i + "]=" + roles[i];
        }

        // Add additional properties
        offset += roles.length;
        System.arraycopy(additionalProps, 0, allProperties, offset, additionalProps.length);

        return new SpringApplicationBuilder(getApplicationClass())
                .web(WebApplicationType.NONE)
                .properties(allProperties)
                .run();
    }

    /**
     * Waits until all 3 nodes in the cluster are in the UP state.
     * This method should be called at the beginning of each test to ensure
     * the cluster is fully initialized before performing any operations.
     *
     * <p>Waits up to 10 seconds for the cluster to initialize.
     *
     * @throws AssertionError if the cluster doesn't initialize within the timeout
     */
    protected void waitUntilClusterInitialized() {
        Cluster cluster = context1.getBean(SpringActorSystem.class).getCluster();
        // Wait until all 3 cluster nodes are UP
        await().atMost(10, SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() -> {
            Assertions.assertNotNull(cluster);
            return cluster.state()
                            .members()
                            .filter(it -> it.status() == MemberStatus.up())
                            .size()
                    == 3;
        });
    }

    /**
     * Waits until the specified number of nodes are in the UP state.
     * Useful for tests that close some nodes and want to wait for the cluster to stabilize.
     *
     * @param expectedNodeCount The expected number of nodes in UP state
     */
    protected void waitUntilClusterHasMembers(int expectedNodeCount) {
        Cluster cluster = context1.getBean(SpringActorSystem.class).getCluster();
        await().atMost(10, SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() -> {
            Assertions.assertNotNull(cluster);
            return cluster.state()
                            .members()
                            .filter(it -> it.status() == MemberStatus.up())
                            .size()
                    == expectedNodeCount;
        });
    }

    /**
     * Finds the specified number of available (unused) ports on the local machine.
     * This helps avoid port conflicts with leftover processes or parallel test runs.
     *
     * @param count The number of available ports to find
     * @return An array of available port numbers
     * @throws RuntimeException if unable to find available ports
     */
    private static int[] findAvailablePorts(int count) {
        int[] ports = new int[count];
        for (int i = 0; i < count; i++) {
            ports[i] = findAvailablePort();
        }
        return ports;
    }

    /**
     * Finds a single available (unused) port on the local machine.
     *
     * @return An available port number
     * @throws RuntimeException if unable to find an available port
     */
    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find available port", e);
        }
    }
}
