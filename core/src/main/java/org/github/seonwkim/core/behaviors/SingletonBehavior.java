package org.github.seonwkim.core.behaviors;

import org.apache.pekko.actor.typed.Behavior;

public interface SingletonBehavior<T> {
	/** Should not collide with existing bean names. */
	String beanName();

	// TODO: maybe we could add dependency inversion box as an argument
	Behavior<T> create();
}
