package io.github.seonwkim.actor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;

/**
 * Imports the actor auto-configurations. <b>Optional in a Spring Boot application:</b> with
 * {@code @SpringBootApplication} (or {@code @EnableAutoConfiguration}) the starter registers them
 * itself, and this annotation adds nothing.
 *
 * <p>It is still the switch where auto-configuration is off, for example in a test slice or a
 * context built without {@code @EnableAutoConfiguration}. The classes are listed in
 * {@code META-INF/spring/io.github.seonwkim.actor.EnableActorSupport.imports} of
 * {@code actor-autoconfigure} and imported as auto-configurations, so they keep their order and
 * their back-off, and a Boot application that also has the annotation loads each one once.
 *
 * <p>{@code spring.actor.enabled=false} turns the actor auto-configurations off either way.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ImportAutoConfiguration
public @interface EnableActorSupport {}
