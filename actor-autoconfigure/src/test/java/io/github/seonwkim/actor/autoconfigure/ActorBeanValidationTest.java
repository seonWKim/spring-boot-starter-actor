package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/** spring.actor.validation.actor-beans: mutable fields and AOP proxies on actor beans. */
@ExtendWith(OutputCaptureExtension.class)
class ActorBeanValidationTest {

    interface Command {}

    abstract static class BaseActor implements SpringActor<Command> {
        int inherited;

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext).build();
        }
    }

    /** Holds state in its own field and in one it inherits. */
    static class CountingActor extends BaseActor {
        int count;
    }

    /** Only wiring: a final field and fields Spring injects, by annotation or as a bean definition property. */
    static class WiredActor implements SpringActor<Command> {
        private final String name = "wired";

        @Autowired
        Environment environment;

        @Value("${greeting:hello}")
        String greeting;

        private Environment setterInjected;

        @Autowired
        void setSetterInjected(Environment environment) {
            this.setterInjected = environment;
        }

        private String configured;

        public void setConfigured(String configured) {
            this.configured = configured;
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext).build();
        }
    }

    static class CountingShardedActor implements SpringShardedActor<Command> {
        int count;

        @Override
        public ShardedEntityType<Command> entityType() {
            return ShardedEntityType.of(Command.class, "counting");
        }

        @Override
        public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
            throw new UnsupportedOperationException();
        }
    }

    /** {@code @Async} makes Spring wrap it in a CGLIB proxy, as {@code @Transactional} would. */
    static class AsyncActor implements SpringActor<Command> {
        @Async
        public void background() {}

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext).build();
        }
    }

    @Configuration
    static class CountingBeans {
        @Bean
        CountingActor countingActor() {
            return new CountingActor();
        }
    }

    @Configuration
    static class ShardedBeans {
        @Bean
        CountingShardedActor countingShardedActor() {
            return new CountingShardedActor();
        }
    }

    @Configuration
    @EnableAsync(proxyTargetClass = true)
    static class ProxiedBeans {
        @Bean
        AsyncActor asyncActor() {
            return new AsyncActor();
        }
    }

    private static ApplicationContextRunner runner(Class<?>... beans) {
        Class<?>[] configurations = new Class<?>[beans.length + 1];
        configurations[0] = ActorConfiguration.class;
        System.arraycopy(beans, 0, configurations, 1, beans.length);
        return new ApplicationContextRunner().withConfiguration(UserConfigurations.of(configurations));
    }

    @Test
    void aMutableFieldWarnsByDefaultIncludingAnInheritedOne(CapturedOutput output) {
        runner(CountingBeans.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output)
                    .contains("WARN")
                    .contains("Actor bean 'countingActor'")
                    .contains("mutable field 'count'")
                    .contains("mutable field 'inherited' (declared in BaseActor)")
                    .contains(ActorBeanValidator.GUIDE);
        });
    }

    @Test
    void aMutableFieldOfAShardedActorWarns(CapturedOutput output) {
        runner(ShardedBeans.class).run(context -> assertThat(output)
                .contains("Actor bean 'countingShardedActor'")
                .contains("mutable field 'count'"));
    }

    @Test
    void finalAndInjectedFieldsDoNotWarn(CapturedOutput output) {
        runner().withBean("wiredActor", WiredActor.class, WiredActor::new, definition -> definition
                        .getPropertyValues()
                        .add("configured", "by the bean definition"))
                .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output).doesNotContain("Actor bean 'wiredActor'");
        });
    }

    @Test
    void failStopsTheContextNamingTheField() {
        runner(CountingBeans.class)
                .withPropertyValues("spring.actor.validation.actor-beans=fail")
                .run(context -> assertThat(context)
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Actor bean 'countingActor'")
                        .hasMessageContaining("mutable field 'count'"));
    }

    @Test
    void offIsSilent(CapturedOutput output) {
        runner(CountingBeans.class)
                .withPropertyValues("spring.actor.validation.actor-beans=off")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(output).doesNotContain("Actor bean 'countingActor'");
                });
    }

    @Test
    void aProxiedActorBeanWarnsAboutTheProxyNotTheProxyClassFields(CapturedOutput output) {
        runner(ProxiedBeans.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output)
                    .contains("Actor bean 'asyncActor' (" + AsyncActor.class.getName() + ") is a Spring AOP proxy")
                    .doesNotContain("mutable field");
        });
    }
}
