package io.github.seonwkim.core.event;

import io.github.seonwkim.core.SpringActor;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that marks a method as an event listener that forwards events to an actor.
 *
 * <p>This annotation is used in conjunction with Spring's {@code @EventListener} annotation
 * to create a bridge between Spring application events and actor messages.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @Component
 * public class SpringEventBridge {
 *
 *     @EventListener
 *     @SendToActor(OrderActor.class)
 *     public OrderActor.CreateOrder onOrderPlaced(OrderPlacedEvent event) {
 *         return new OrderActor.CreateOrder(event.getOrderId(), event.getAmount());
 *     }
 * }
 * }
 * </pre>
 *
 * <p>The annotated method must:
 * <ul>
 *   <li>Be annotated with {@code @EventListener}</li>
 *   <li>Return a message type that the target actor can handle</li>
 *   <li>Have the target actor class specified in the {@link #value()} attribute</li>
 * </ul>
 *
 * <p>The framework will automatically:
 * <ul>
 *   <li>Detect methods annotated with both {@code @EventListener} and {@code @SendToActor}</li>
 *   <li>Spawn or get the target actor instance</li>
 *   <li>Send the returned message to the actor</li>
 * </ul>
 *
 * @see org.springframework.context.event.EventListener
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SendToActor {

    /**
     * The target actor class to which messages should be sent.
     *
     * @return the actor class
     */
    Class<? extends SpringActor<?>> value();

    /**
     * The actor ID to use when spawning or getting the actor.
     * If not specified, a default ID will be generated based on the actor class name.
     *
     * @return the actor ID, or empty string to use default
     */
    String actorId() default "";
}
