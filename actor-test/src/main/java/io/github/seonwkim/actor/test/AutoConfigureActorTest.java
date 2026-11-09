package io.github.seonwkim.actor.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/**
 * The auto-configuration an {@link ActorTest} imports: configuration properties, Jackson and the
 * task executor, which the actor system uses when they are there. The list is in
 * {@code META-INF/spring/io.github.seonwkim.actor.test.AutoConfigureActorTest.imports}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ImportAutoConfiguration
public @interface AutoConfigureActorTest {}
