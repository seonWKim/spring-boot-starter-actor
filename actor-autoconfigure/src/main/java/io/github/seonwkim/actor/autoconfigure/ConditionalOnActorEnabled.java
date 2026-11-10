package io.github.seonwkim.actor.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Applies only when {@code spring.actor.enabled} is not {@code false}. Every actor
 * auto-configuration carries it, so the one property turns them all off.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "spring.actor.enabled", matchIfMissing = true)
public @interface ConditionalOnActorEnabled {}
