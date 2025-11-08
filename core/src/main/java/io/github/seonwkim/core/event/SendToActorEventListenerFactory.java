package io.github.seonwkim.core.event;

import io.github.seonwkim.core.SpringActor;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Factory that creates ApplicationListener instances for methods annotated with {@link SendToActor}.
 *
 * <p>This factory integrates with Spring's event listener infrastructure to automatically
 * forward event listener results to actors.
 */
public class SendToActorEventListenerFactory implements EventListenerFactory, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SendToActorEventListenerFactory.class);

    private final SendToActorBeanPostProcessor processor;

    public SendToActorEventListenerFactory(SendToActorBeanPostProcessor processor) {
        this.processor = processor;
    }

    @Override
    public boolean supportsMethod(Method method) {
        return AnnotationUtils.findAnnotation(method, SendToActor.class) != null;
    }

    @Override
    public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
        SendToActor sendToActor = AnnotationUtils.findAnnotation(method, SendToActor.class);
        if (sendToActor == null) {
            throw new IllegalStateException("@SendToActor annotation not found on method: " + method.getName());
        }

        log.info(
                "Creating event listener for @SendToActor method {} targeting actor {}",
                method.getName(),
                sendToActor.value().getSimpleName());

        return event -> {
            try {
                // Get application context
                if (processor.getApplicationContext() == null) {
                    log.error("ApplicationContext not available in SendToActorBeanPostProcessor");
                    return;
                }

                // Get the bean instance
                Object bean = processor.getApplicationContext().getBean(beanName);

                // Make method accessible
                method.setAccessible(true);

                // Invoke the method with the event
                Object result = method.invoke(bean, event);

                if (result != null) {
                    // Get actor class and ID
                    Class<? extends SpringActor<?>> actorClass = sendToActor.value();
                    String actorId = sendToActor.actorId();

                    if (!StringUtils.hasText(actorId)) {
                        actorId = SendToActorBeanPostProcessor.getDefaultActorId(actorClass);
                    }

                    // Send message to actor
                    processor.sendToActor(actorClass, actorId, result);
                }
            } catch (Exception e) {
                log.error("Failed to process @SendToActor method {}", method.getName(), e);
            }
        };
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
