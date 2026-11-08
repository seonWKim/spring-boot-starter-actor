package io.github.seonwkim.core;

import io.github.seonwkim.core.management.PekkoManagementAutoConfiguration;
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
 *   <li>{@link ActorConfiguration} - Core actor system configuration
 *   <li>{@link PekkoManagementAutoConfiguration} - Optional Pekko Management (if dependencies present)
 * </ul>
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
@Import({ActorConfiguration.class, PekkoManagementAutoConfiguration.class})
public @interface EnableActorSupport {}
