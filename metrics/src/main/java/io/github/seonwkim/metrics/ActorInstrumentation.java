package io.github.seonwkim.metrics;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.seonwkim.metrics.listener.ActorInstrumentationEventListener;
import io.github.seonwkim.metrics.listener.ActorSystemEventListener;

public class ActorInstrumentation {
	private static final Logger logger = LoggerFactory.getLogger(ActorInstrumentation.class);
	
	public static AgentBuilder decorate(AgentBuilder builder) {
		return builder
				.type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
				.transform(new ActorInstrumentationTransformer())
				.type(ElementMatchers.named("org.apache.pekko.actor.UnstartedCell"))
				.transform(new UnstartedCellTransformer());
	}

	public static class ActorInstrumentationTransformer implements Transformer {
		@Override
		public Builder<?> transform(
				Builder<?> builder,
				TypeDescription typeDescription,
				ClassLoader classLoader,
				JavaModule module) {
			return builder
					// Track actor lifecycle - creation
					.visit(
							Advice.to(ActorCellConstructorAdvice.class)
									.on(ElementMatchers.isConstructor()))
					// Track actor lifecycle - termination
					.visit(
							Advice.to(ActorCellTerminateAdvice.class)
									.on(ElementMatchers.named("terminate")))
					// Track message processing
					.visit(
							Advice.to(ActorInstrumentation.InvokeAdvice.class)
									.on(ElementMatchers.named("invoke")))
					// Track batch message processing
					.visit(
							Advice.to(ActorInstrumentation.InvokeAllAdvice.class)
									.on(ElementMatchers.named("invokeAll$1")));
		}
	}
	
	public static class UnstartedCellTransformer implements Transformer {
		@Override
		public Builder<?> transform(
				Builder<?> builder,
				TypeDescription typeDescription,
				ClassLoader classLoader,
				JavaModule module) {
			return builder
					// Track when UnstartedCell is replaced with ActorCell
					.visit(
							Advice.to(UnstartedCellReplaceWithAdvice.class)
									.on(ElementMatchers.named("replaceWith")));
		}
	}

	public static class InvokeAdvice {

		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static long onEnter(
				@Advice.Argument(0) Object envelope, @Advice.Local("envelopRef") Object envelopeRef) {
			ActorInstrumentationEventListener.invokeAdviceOnEnter(envelope);
			envelopeRef = envelope;
			return System.nanoTime();
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class)
		public static void onExit(
				@Advice.Local("envelopRef") Object envelopeRef, @Advice.Enter long startTime) {
			ActorInstrumentationEventListener.invokeAdviceOnExit(envelopeRef, startTime);
		}
	}

	public static class InvokeAllAdvice {

		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static long onEnter(
				@Advice.Argument(0) Object messages, @Advice.Local("messagesRef") Object messagesRef) {
			ActorInstrumentationEventListener.invokeAllAdviceOnEnter(messages);
			messagesRef = messages;
			return System.nanoTime();
		}

		@Advice.OnMethodExit(onThrowable = Throwable.class)
		public static void onExit(
				@Advice.Local("messagesRef") Object messagesRef, @Advice.Enter long startTime) {
			ActorInstrumentationEventListener.invokeAllAdviceOnExit(messagesRef, startTime);
		}
	}
	
	public static class ActorCellConstructorAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.This Object cell) {
			ActorSystemEventListener.onActorCreated(cell);
		}
	}
	
	public static class ActorCellTerminateAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.This Object cell) {
			ActorSystemEventListener.onActorTerminated(cell);
		}
	}
	
	public static class UnstartedCellReplaceWithAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.This Object unstartedCell, @Advice.Argument(0) Object newCell) {
			ActorSystemEventListener.onUnstartedCellReplaced(unstartedCell, newCell);
		}
	}
	
}
