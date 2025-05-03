package org.github.seonwkim.core;

import org.github.seonwkim.core.common.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties(prefix = "pekko")
public class PekkoConfiguration {

	private final String name;
	private final Actor actor;
	@Nullable private final Remote remote;
	@Nullable private final Cluster cluster;

	public PekkoConfiguration(
			String name, Actor actor, @Nullable Remote remote, @Nullable Cluster cluster) {
		this.name = name;
		this.actor = actor;
		this.remote = remote;
		this.cluster = cluster;
	}

	public String getName() {
		return name;
	}

	public Actor getActor() {
		return actor;
	}

	@Nullable
	public Remote getRemote() {
		return remote;
	}

	@Nullable
	public Cluster getCluster() {
		return cluster;
	}

	public boolean isClusterMode() {
		return this.cluster != null;
	}

	@ConstructorBinding
	public static class Actor {
		private final String provider;
		@Nullable private final String allowJavaSerialization;
		@Nullable private final String warnAboutJavaSerializerUsage;

		public Actor(
				String provider,
				@Nullable String allowJavaSerialization,
				@Nullable String warnAboutJavaSerializerUsage) {
			this.provider = provider;
			this.allowJavaSerialization = allowJavaSerialization;
			this.warnAboutJavaSerializerUsage = warnAboutJavaSerializerUsage;
		}

		public String getProvider() {
			return provider;
		}

		@Nullable
		public String getAllowJavaSerialization() {
			return allowJavaSerialization;
		}

		@Nullable
		public String getWarnAboutJavaSerializerUsage() {
			return warnAboutJavaSerializerUsage;
		}
	}

	@ConstructorBinding
	public static class Remote {
		private final Artery artery;

		public Remote(Artery artery) {
			this.artery = artery;
		}

		public Artery getArtery() {
			return artery;
		}

		@ConstructorBinding
		public static class Artery {
			private final Canonical canonical;

			public Artery(Canonical canonical) {
				this.canonical = canonical;
			}

			public Canonical getCanonical() {
				return canonical;
			}

			@ConstructorBinding
			public static class Canonical {
				private final String hostname;
				private final int port;

				public Canonical(String hostname, int port) {
					this.hostname = hostname;
					this.port = port;
				}

				public String getHostname() {
					return hostname;
				}

				public int getPort() {
					return port;
				}
			}
		}
	}

	@ConstructorBinding
	public static class Cluster {
		private final String name;
		private final String[] seedNodes;
		private final String downingProviderClass;

		public Cluster(String name, String[] seedNodes, String downingProviderClass) {
			for (String seedNode : seedNodes) {
				if (!seedNode.matches("pekko://" + name + "@.*")) {
					throw new IllegalArgumentException(
							"Invalid seed node: "
									+ seedNode
									+ ". Expected format: pekko://"
									+ name
									+ "@<hostname>:<port>");
				}
			}

			this.name = name;
			this.seedNodes = seedNodes;
			this.downingProviderClass = downingProviderClass;
		}

		public String getName() {
			return name;
		}

		public String[] getSeedNodes() {
			return seedNodes;
		}

		public String getDowningProviderClass() {
			return downingProviderClass;
		}
	}
}
