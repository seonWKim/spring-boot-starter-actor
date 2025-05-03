package org.github.seonwkim.core.behaviors;

import org.apache.pekko.actor.typed.Behavior;
import org.github.seonwkim.core.DependencyContainer;

public interface RootBehavior {
	Behavior<String> create(DependencyContainer container);
}
