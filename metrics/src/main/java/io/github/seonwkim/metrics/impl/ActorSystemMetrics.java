package io.github.seonwkim.metrics.impl;

import java.util.concurrent.atomic.AtomicLong;

public class ActorSystemMetrics {
	private static final ActorSystemMetrics INSTANCE = new ActorSystemMetrics();
	
	private final AtomicLong activeActors = new AtomicLong(0);
	
	private ActorSystemMetrics() {}
	
	public static ActorSystemMetrics getInstance() {
		return INSTANCE;
	}
	
	public void incrementActiveActors() {
		activeActors.incrementAndGet();
	}
	
	public void decrementActiveActors() {
		activeActors.decrementAndGet();
	}
	
	public long getActiveActors() {
		return activeActors.get();
	}
	
	public void reset() {
		activeActors.set(0);
	}
}
