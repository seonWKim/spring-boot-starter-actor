package io.github.seonwkim.metrics;

import java.lang.instrument.Instrumentation;

import org.apache.pekko.dispatch.Envelope;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class ActorInstrumentation {
    public static void install(Instrumentation instrumentation) {
        AgentBuilder agentBuilder = new AgentBuilder.Default()
                .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform(new ActorInstrumentationTransformer());

        agentBuilder.installOn(instrumentation);
    }

    public static class ActorInstrumentationTransformer implements Transformer {
        @Override
        public Builder<?> transform(Builder<?> builder,
                                    TypeDescription typeDescription,
                                    ClassLoader classLoader,
                                    JavaModule module) {
            return builder.visit(Advice.to(ActorInstrumentation.InvokeAdvice.class)
                                       .on(ElementMatchers.named("invoke")))
                          .visit(Advice.to(ActorInstrumentation.SystemInvokeAdvice.class)
                                       .on(ElementMatchers.named("systemInvoke")));
        }
    }

    public static class InvokeAdvice {

        @Advice.OnMethodEnter
        public static long onEnter(@Advice.Argument(0) Envelope envelope,
                                   @Advice.Local("envelopRef") Envelope envelopeRef) {
            ActorInstrumentationEventListener.invokeAdviceOnEnter(envelope);
            envelopeRef = envelope;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Local("envelopRef") Envelope envelopeRef,
                                  @Advice.Enter long startTime,
                                  @Advice.Thrown Throwable throwable) {
            ActorInstrumentationEventListener.invokeAdviceOnExit(envelopeRef, startTime, throwable);
        }
    }

    public static class SystemInvokeAdvice {

        @Advice.OnMethodEnter
        public static long onEnter(@Advice.Argument(0) Object systemMessage,
                                   @Advice.Local("systemMessageRef") Object systemMessageRef) {
            ActorInstrumentationEventListener.systemInvokeAdviceOnEnter(systemMessage);
            systemMessageRef = systemMessage;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Local("systemMessageRef") Object systemMessageRef,
                                  @Advice.Enter long startTime,
                                  @Advice.Thrown Throwable throwable) {
            ActorInstrumentationEventListener.systemInvokeAdviceOnExit(systemMessageRef, startTime, throwable);
        }
    }
}
