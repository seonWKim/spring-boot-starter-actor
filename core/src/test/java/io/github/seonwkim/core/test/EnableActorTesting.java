package io.github.seonwkim.core.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables actor testing in a Spring Boot test context by importing the necessary testing
 * configuration.
 *
 * <p>This annotation imports {@link ActorTestConfiguration} which auto-configures the {@link
 * SpringActorTestKit} bean and related testing infrastructure.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @SpringBootTest
 * @EnableActorTesting
 * public class OrderActorTest {
 *
 *     @Autowired
 *     private SpringActorTestKit testKit;
 *
 *     @Test
 *     public void testOrderCreation() {
 *         testKit.forActor(OrderActor.class)
 *             .withId("test-order")
 *             .spawn()
 *             .send(new CreateOrder("order-1", 100.0))
 *             .expectReply(OrderCreated.class);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ActorTestConfiguration.class)
public @interface EnableActorTesting {}
