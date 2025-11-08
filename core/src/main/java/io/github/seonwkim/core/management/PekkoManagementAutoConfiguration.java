package io.github.seonwkim.core.management;

import io.github.seonwkim.core.SpringActorSystem;
import javax.annotation.PostConstruct;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.javadsl.PekkoManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Pekko Management and Cluster Bootstrap.
 *
 * <p>This configuration automatically starts:
 * <ul>
 *   <li>Pekko Management HTTP server (port 8558 by default)
 *   <li>Cluster Bootstrap for dynamic cluster formation
 * </ul>
 *
 * <p>It only activates when:
 * <ul>
 *   <li>Pekko Management classes are on the classpath
 *   <li>The actor system is in cluster mode
 *   <li>Management is not explicitly disabled via configuration
 * </ul>
 *
 * <p>To disable auto-start, set:
 * <pre>
 * spring.actor.pekko.management.enabled=false
 * </pre>
 *
 * <p>Required dependencies (users must add these explicitly):
 * <pre>
 * implementation("org.apache.pekko:pekko-management-cluster-bootstrap_3:1.1.1")
 * implementation("org.apache.pekko:pekko-management-cluster-http_3:1.1.1")
 *
 * Additionally, for Kubernetes deployments:
 * implementation("org.apache.pekko:pekko-discovery-kubernetes-api_3:1.1.1")
 * </pre>
 */
@Configuration
@ConditionalOnClass(
        name = {
            "org.apache.pekko.management.javadsl.PekkoManagement",
            "org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap"
        })
@ConditionalOnProperty(value = "spring.actor.pekko.management.enabled", matchIfMissing = true)
public class PekkoManagementAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PekkoManagementAutoConfiguration.class);

    private final SpringActorSystem actorSystem;

    public PekkoManagementAutoConfiguration(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @PostConstruct
    public void startManagement() {
        // Only start management if in cluster mode
        if (actorSystem.getCluster() == null) {
            log.debug("Pekko Management not started - actor system is not in cluster mode");
            return;
        }

        try {
            // Start Pekko Management HTTP server
            PekkoManagement management =
                    PekkoManagement.get(actorSystem.getRaw().classicSystem());
            management.start();
            log.info("Pekko Management HTTP server started");

            // Start Cluster Bootstrap for dynamic cluster formation
            ClusterBootstrap bootstrap =
                    ClusterBootstrap.get(actorSystem.getRaw().classicSystem());
            bootstrap.start();
            log.info("Pekko Cluster Bootstrap started - cluster will form dynamically");

        } catch (Exception e) {
            log.error("Failed to start Pekko Management or Cluster Bootstrap", e);
            throw new RuntimeException("Failed to start Pekko Management", e);
        }
    }
}
