package org.github.seonwkim.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.github.seonwkim.core.PekkoConfiguration;
import org.junit.jupiter.api.Test;

public class PekkoConfigurationUtilsTest {

	@Test
	public void test_toPropertiesString() {
		String name = "pekko-actor";
		PekkoConfiguration.Actor actor = new PekkoConfiguration.Actor("cluster", "on", "off");
		PekkoConfiguration.Remote.Artery.Canonical canonical =
				new PekkoConfiguration.Remote.Artery.Canonical("127.0.0.1", 2551);
		PekkoConfiguration.Remote.Artery artery = new PekkoConfiguration.Remote.Artery(canonical);
		PekkoConfiguration.Remote remote = new PekkoConfiguration.Remote(artery);
		PekkoConfiguration.Cluster cluster =
				new PekkoConfiguration.Cluster(
						"clusterName",
						new String[] {
							"pekko://clusterName@127.0.0.1:2551",
							"pekko://clusterName@127.0.0.1:2552",
							"pekko://clusterName@127.0.0.1:2553"
						},
						"org.apache.pekko.cluster.sbr.SplitBrainResolverProvider");

		PekkoConfiguration pekkoConfiguration = new PekkoConfiguration(name, actor, remote, cluster);

		String propertiesString = PekkoConfigurationUtils.toPropertiesString(pekkoConfiguration);

		assertThat(propertiesString).isNotNull();
		assertThat(propertiesString).contains("pekko.actor.provider = \"cluster\"");
		assertThat(propertiesString).contains("pekko.actor.allow-java-serialization = \"on\"");
		assertThat(propertiesString).contains("pekko.actor.warn-about-java-serializer-usage = \"off\"");
		assertThat(propertiesString).contains("pekko.remote.artery.canonical.hostname = \"127.0.0.1\"");
		assertThat(propertiesString).contains("pekko.remote.artery.canonical.port = \"2551\"");
		assertThat(propertiesString)
				.contains(
						"pekko.cluster.seed-nodes = [\n    \"pekko://clusterName@127.0.0.1:2551\",\n    \"pekko://clusterName@127.0.0.1:2552\",\n    \"pekko://clusterName@127.0.0.1:2553\",\n]");
		assertThat(propertiesString)
				.contains(
						"pekko.cluster.downing-provider-class = \"org.apache.pekko.cluster.sbr.SplitBrainResolverProvider\"");
	}
}
