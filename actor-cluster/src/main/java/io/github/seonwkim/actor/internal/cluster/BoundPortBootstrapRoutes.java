package io.github.seonwkim.actor.internal.cluster;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.http.scaladsl.server.RequestContext;
import org.apache.pekko.http.scaladsl.server.RouteResult;
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap;
import org.apache.pekko.management.cluster.bootstrap.contactpoint.HttpClusterBootstrapRoutes;
import org.apache.pekko.management.scaladsl.ManagementRouteProvider;
import org.apache.pekko.management.scaladsl.ManagementRouteProviderSettings;
import org.apache.pekko.management.scaladsl.PekkoManagement;
import scala.Function1;
import scala.concurrent.Future;
import scala.jdk.javaapi.FutureConverters;

/**
 * Cluster Bootstrap's management routes, with the self contact point at the port the management
 * server bound. Bootstrap's own route provider takes the configured port, which is wrong when it is
 * 0: the node then never finds itself among the discovered contact points and no node forms the
 * cluster. {@link PekkoClusterSupport} puts this class in place of it
 * ({@code pekko.management.http.routes.cluster-bootstrap}).
 */
public final class BoundPortBootstrapRoutes implements ManagementRouteProvider {

    private final ExtendedActorSystem system;

    public BoundPortBootstrapRoutes(ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public Function1<RequestContext, Future<RouteResult>> routes(ManagementRouteProviderSettings settings) {
        ClusterBootstrap bootstrap = ClusterBootstrap.get(system);
        // Called while the server starts: start() returns the URI it binds, with the bound port.
        FutureConverters.asJava(PekkoManagement.get(system).start()).thenAccept(bootstrap::setSelfContactPoint);
        return new HttpClusterBootstrapRoutes(bootstrap.settings()).routes();
    }
}
