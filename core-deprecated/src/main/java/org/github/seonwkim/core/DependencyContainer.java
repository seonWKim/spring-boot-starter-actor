package org.github.seonwkim.core;

import java.util.List;

import org.github.seonwkim.core.behaviors.RootBehavior;
import org.github.seonwkim.core.behaviors.SingletonBehavior;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DependencyContainer {
	private final GenericApplicationContext applicationContext;
	private final RootBehavior rootBehavior;
	private final List<SingletonBehavior<?>> singletonBehaviors;
	public DependencyContainer(
            GenericApplicationContext applicationContext,
            RootBehavior rootBehavior,
            List<SingletonBehavior<?>> singletonBehaviors) {
		this.applicationContext = applicationContext;
		this.rootBehavior = rootBehavior;
		this.singletonBehaviors = singletonBehaviors;
    }

	public GenericApplicationContext getApplicationContext() {
		return applicationContext;
	}

	public RootBehavior getRootBehavior() {
		return rootBehavior;
	}

	public List<SingletonBehavior<?>> getSingletonBehaviors() {
		return singletonBehaviors;
	}
}
