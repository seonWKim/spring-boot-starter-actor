package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.utils.MetricsUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Registers cluster metrics from the cluster system to Micrometer. */
@Component
public class ActorClusterMetricsExporter {

	private final SpringActorSystem springActorSystem;
	private final MeterRegistry registry;

	public ActorClusterMetricsExporter(SpringActorSystem springActorSystem, MeterRegistry registry) {
		this.springActorSystem = springActorSystem;
		this.registry = registry;
	}

	@PostConstruct
	public void registerMetrics() {
		Tags tags = Tags.of("application", "spring-pekko");
		registry.gauge(
				"actor_cluster_members_total", tags, springActorSystem, MetricsUtils::getMemberCount);
		registry.gauge("actor_cluster_members_up", tags, springActorSystem, MetricsUtils::getUpCount);
		registry.gauge(
				"actor_cluster_members_unreachable",
				tags,
				springActorSystem,
				MetricsUtils::getUnreachableCount);
		registry.gauge(
				"actor_cluster_self_roles_count",
				tags,
				springActorSystem,
				s -> MetricsUtils.getSelfRoles(s).size());
	}
}
