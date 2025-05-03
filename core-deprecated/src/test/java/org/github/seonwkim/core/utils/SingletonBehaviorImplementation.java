package org.github.seonwkim.core.utils;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.behaviors.SingletonBehavior;
import org.github.seonwkim.core.utils.SingletonBehaviorImplementation.Command;
import org.springframework.stereotype.Component;

// for testing purposes
@Component
public class SingletonBehaviorImplementation implements SingletonBehavior<Command> {

	public static final String BEAN_NAME = "system-singleton-behavior-implementation";

	interface Command {};

	@Override
	public String beanName() {
		return BEAN_NAME;
	}

	@Override
	public Behavior<Command> create() {
		return Behaviors.stopped();
	}
}
