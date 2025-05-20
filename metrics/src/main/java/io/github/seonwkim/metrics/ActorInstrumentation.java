package io.github.seonwkim.metrics;

import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

public class ActorInstrumentation {
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                    // .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                    .transform((builder, typeDescription, classLoader, module, pd) -> builder
                            .visit(Advice.to(ActorInstrumentation.InvokeAdvice.class).on(ElementMatchers.named("invoke")))
                            .visit(Advice.to(ActorInstrumentation.InvokeAllAdvice.class).on(ElementMatchers.named("invokeAll")))
                    ).installOn(instrumentation);
    }

    public static class InvokeAdvice {

        @Advice.OnMethodEnter
        public static long onEnter(@Advice.Argument(0) Object envelope) {
            // You can cast to Envelope if needed (use ASM-safe types if working across classloaders)
            long start = System.nanoTime();
            System.out.println("invoke onEnter: " + envelope);
            // You can also extract actor path, message type, etc.
            return start;
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Enter long startTime,
                                  @Advice.Thrown Throwable throwable) {
            long elapsed = System.nanoTime() - startTime;
            System.out.println("invoke onExit: ");
            if (throwable != null) {
                // Report failure, log, or increment failure metric
            } else {
                // Report success, latency, etc.
            }
        }
    }

    public static class InvokeAllAdvice {

        @Advice.OnMethodEnter
        public static long onEnter(@Advice.Argument(0) Object msgList) {
            System.out.println("invokeAll onEnter: " + msgList);
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Enter long start,
                                  @Advice.Thrown Throwable throwable) {
            long elapsed = System.nanoTime() - start;
            System.out.println("invoke onExit: ");
            // Same as above: record metric or trace
        }
    }

}
