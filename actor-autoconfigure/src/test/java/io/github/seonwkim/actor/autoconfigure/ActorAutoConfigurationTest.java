package io.github.seonwkim.actor.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.RootGuardian;
import io.github.seonwkim.actor.internal.RootGuardianSupplierWrapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The starter starts through Spring Boot's auto-configuration, with or without
 * {@code @EnableActorSupport}, backs off for the application's own beans and turns off with
 * {@code spring.actor.enabled=false}.
 */
class ActorAutoConfigurationTest {

    private static final String STARTER_PACKAGE = "io.github.seonwkim.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void theActorSystemStartsWithoutTheAnnotation() {
        runner.withUserConfiguration(BootApp.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SpringActorSystem.class));
    }

    @Test
    void theAnnotationStartsTheActorSystemWhereAutoConfigurationIsOff() {
        runner.withUserConfiguration(AnnotationOnly.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(SpringActorSystem.class));
    }

    /**
     * The application's bean replaces the starter's, also when the annotation is on the class that
     * defines it: a plain {@code @Import} would register the starter's bean first and keep both.
     */
    @ParameterizedTest
    @ValueSource(classes = {OwnRootGuardian.class, OwnRootGuardianWithAnnotation.class})
    void anApplicationBeanReplacesTheStartersBean(Class<?> application) {
        runner.withUserConfiguration(application).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RootGuardianSupplierWrapper.class)).isSameAs(OWN_ROOT_GUARDIAN);
        });
    }

    @ParameterizedTest
    @ValueSource(classes = {BootApp.class, BootAppWithAnnotation.class, AnnotationOnly.class})
    void enabledFalseTurnsEveryActorAutoConfigurationOff(Class<?> application) {
        runner.withUserConfiguration(application)
                .withPropertyValues("spring.actor.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    List<String> starterBeans = Arrays.stream(context.getBeanDefinitionNames())
                            .map(context::getType)
                            .filter(Objects::nonNull)
                            .map(Class::getName)
                            .filter(type -> type.startsWith(STARTER_PACKAGE))
                            .filter(type -> !type.startsWith(ActorAutoConfigurationTest.class.getName()))
                            .toList();
                    assertThat(starterBeans).isEmpty();
                });
    }

    /** Where auto-configuration is off, the annotation must bring every configuration the starter registers. */
    @Test
    void theAnnotationImportsEveryRegisteredAutoConfiguration() {
        ClassLoader classLoader = getClass().getClassLoader();
        Set<String> registered = new HashSet<>();
        ImportCandidates.load(AutoConfiguration.class, classLoader).forEach(name -> {
            if (name.startsWith(ActorConfiguration.class.getPackageName() + ".")) {
                registered.add(name);
            }
        });
        Set<String> imported = new HashSet<>();
        ImportCandidates.load(EnableActorSupport.class, classLoader).forEach(imported::add);

        assertThat(registered).contains(ActorConfiguration.class.getName());
        assertThat(imported).isEqualTo(registered);
    }

    static final RootGuardianSupplierWrapper OWN_ROOT_GUARDIAN = new RootGuardianSupplierWrapper(RootGuardian::create);

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class BootApp {}

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableActorSupport
    static class BootAppWithAnnotation {}

    @Configuration(proxyBeanMethods = false)
    @EnableActorSupport
    static class AnnotationOnly {}

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class OwnRootGuardian {
        @Bean
        RootGuardianSupplierWrapper ownRootGuardian() {
            return OWN_ROOT_GUARDIAN;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableActorSupport
    static class OwnRootGuardianWithAnnotation {
        @Bean
        RootGuardianSupplierWrapper ownRootGuardian() {
            return OWN_ROOT_GUARDIAN;
        }
    }
}
