package io.github.seonwkim.actor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables actor support in a Spring Boot application by importing the necessary configuration.
 *
 * <p>This annotation imports:
 * <ul>
 *   <li>{@code io.github.seonwkim.actor.autoconfigure.ActorConfiguration} - Core actor system configuration
 *   <li>{@code io.github.seonwkim.actor.autoconfigure.management.PekkoManagementAutoConfiguration} - Optional
 *       Pekko Management (if dependencies present)
 * </ul>
 *
 * <p>The classes live in {@code actor-autoconfigure} and are imported by name, so this annotation
 * stays in {@code actor-api} without depending on the modules that implement it.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableActorSupport
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ActorSupportImportSelector.class)
public @interface EnableActorSupport {}
