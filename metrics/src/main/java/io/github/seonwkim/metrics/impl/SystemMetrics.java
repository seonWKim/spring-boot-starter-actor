package io.github.seonwkim.metrics.impl;

import java.util.concurrent.atomic.AtomicLong;

public class SystemMetrics {
	private static final SystemMetrics INSTANCE = new SystemMetrics();
	
	private final AtomicLong activeActors = new AtomicLong(0);
	
	private SystemMetrics() {}
	
	public static SystemMetrics getInstance() {
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
