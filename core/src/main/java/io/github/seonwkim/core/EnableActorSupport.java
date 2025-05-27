package io.github.seonwkim.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * Enables actor support in a Spring Boot application by importing the necessary configuration.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableActorSupport
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(PekkoConfiguration.class)
public @interface EnableActorSupport {
}
