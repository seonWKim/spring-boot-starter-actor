package org.github.seonwkim.example.behaviors;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.behaviors.SingletonBehavior;
import org.springframework.stereotype.Component;

@Component
public class WorldBehavior implements SingletonBehavior<WorldBehavior.Command> {
	@Override
	public String beanName() {
		return "system-world-behavior";
	}

	@Override
	public Behavior<Command> create() {
		return Behaviors.setup(
				context ->
						Behaviors.receive(Command.class)
								.onMessage(HelloCommand.class, command -> handleHelloCommand())
								.onMessage(HiCommand.class, command -> handleHiCommand())
								.build());
	}

	private Behavior<Command> handleHelloCommand() {
		System.out.println("hello world");
		return Behaviors.same();
	}

	private Behavior<Command> handleHiCommand() {
		System.out.println("hi world");
		return Behaviors.same();
	}

	public interface Command {}

	public static class HelloCommand implements Command {}

	public static class HiCommand implements Command {}
}
