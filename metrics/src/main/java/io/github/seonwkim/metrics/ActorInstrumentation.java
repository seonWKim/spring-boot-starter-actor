package io.github.seonwkim.metrics;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class ActorInstrumentation {
    public static AgentBuilder decorate(AgentBuilder builder) {
        return builder
                .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform(new ActorInstrumentationTransformer());
    }

    public static class ActorInstrumentationTransformer implements Transformer {
        @Override
        public Builder<?> transform(Builder<?> builder,
                                    TypeDescription typeDescription,
                                    ClassLoader classLoader,
                                    JavaModule module) {
            return builder
                    .visit(Advice.to(ActorInstrumentation.InvokeAdvice.class)
                                 .on(ElementMatchers.named("invoke")))
                    .visit(Advice.to(ActorInstrumentation.InvokeAllAdvice.class)
                                 .on(ElementMatchers.named("invokeAll$1")));
        }
    }

    public static class InvokeAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static long onEnter(@Advice.Argument(0) Object envelope,
                                   @Advice.Local("envelopRef") Object envelopeRef) {
            ActorInstrumentationEventListener.invokeAdviceOnEnter(envelope);
            envelopeRef = envelope;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Local("envelopRef") Object envelopeRef,
                                  @Advice.Enter long startTime) {
            ActorInstrumentationEventListener.invokeAdviceOnExit(envelopeRef, startTime);
        }
    }

    public static class InvokeAllAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static long onEnter(@Advice.Argument(0) Object messages,
                                   @Advice.Local("messagesRef") Object messagesRef) {
            ActorInstrumentationEventListener.invokeAllAdviceOnEnter(messages);
            messagesRef = messages;
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Local("messagesRef") Object messagesRef,
                                  @Advice.Enter long startTime) {
            ActorInstrumentationEventListener.invokeAllAdviceOnExit(messagesRef, startTime);
        }
    }
}
