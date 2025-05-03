package org.github.seonwkim.core.behaviors.impl;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.ActorRefWrapper;
import org.github.seonwkim.core.DependencyContainer;
import org.github.seonwkim.core.behaviors.RootBehavior;

public class DefaultRootBehavior implements RootBehavior {
	@Override
	public Behavior<String> create(DependencyContainer container) {
		return Behaviors.setup(
				context -> {
					context.getLog().debug("Non-cluster behavior started");

					container
							.getSingletonBehaviors()
							.forEach(
									behavior -> {
										ActorRef<?> actorRef = context.spawn(behavior.create(), behavior.beanName());

										ActorRefWrapper<?> wrapper =
												new ActorRefWrapper() {
													@Override
													public String beanName() {
														return behavior.beanName();
													}

													@Override
													public ActorRef unwrap() {
														return actorRef;
													}
												};
										container
												.getApplicationContext()
												.registerBean(behavior.beanName(), ActorRefWrapper.class, () -> wrapper);
									});

					return Behaviors.receiveMessage(
							message -> {
								context.getLog().debug("Received message: {}", message);
								return Behaviors.same();
							});
				});
	}
}
