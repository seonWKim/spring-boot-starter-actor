package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.utils.MetricsUtils;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "pekko-cluster-metrics")
public class PekkoClusterMetricsEndpoint {

	private final SpringActorSystem springActorSystem;

	public PekkoClusterMetricsEndpoint(SpringActorSystem springActorSystem) {
		this.springActorSystem = springActorSystem;
	}

	@ReadOperation
	public Map<String, Object> clusterMetrics() {
		return MetricsUtils.getClusterMetrics(springActorSystem);
	}
}
