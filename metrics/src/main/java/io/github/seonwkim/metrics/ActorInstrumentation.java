package io.github.seonwkim.metrics;

import java.lang.instrument.Instrumentation;

import org.apache.pekko.dispatch.Envelope;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

public class ActorInstrumentation {
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builder, typeDescription, classLoader, module, pd) -> builder
                        .visit(Advice.to(ActorInstrumentation.InvokeAdvice.class)
                                     .on(ElementMatchers.named("invoke")))
                ).installOn(instrumentation);
    }

    public static class InvokeAdvice {

        @Advice.OnMethodEnter
        public static long onEnter(@Advice.Argument(0) Object envelope) {
            ActorInstrumentationEventListener.invokeAdviceOnEnter((Envelope) envelope);
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Enter long startTime,
                                  @Advice.Thrown Throwable throwable) {
            ActorInstrumentationEventListener.invokeAdviceOnExit(startTime, throwable);
        }
    }
}
