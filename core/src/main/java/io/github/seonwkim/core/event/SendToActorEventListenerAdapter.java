package io.github.seonwkim.core.event;

import io.github.seonwkim.core.SpringActor;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Adapter that wraps event listener methods annotated with {@link SendToActor}
 * to forward their results to actors.
 *
 * <p>This adapter intercepts the execution of event listener methods and:
 * <ol>
 *   <li>Invokes the original method to get the actor message</li>
 *   <li>Spawns or gets the target actor</li>
 *   <li>Sends the message to the actor</li>
 * </ol>
 *
 * <p>This class works in conjunction with {@link SendToActorBeanPostProcessor}.
 */
public class SendToActorEventListenerAdapter implements ApplicationListener<ApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(SendToActorEventListenerAdapter.class);

    private final Object targetBean;
    private final Method targetMethod;
    private final SendToActor sendToActorAnnotation;
    private final SendToActorBeanPostProcessor processor;

    public SendToActorEventListenerAdapter(
            Object targetBean,
            Method targetMethod,
            SendToActor sendToActorAnnotation,
            SendToActorBeanPostProcessor processor) {
        this.targetBean = targetBean;
        this.targetMethod = targetMethod;
        this.sendToActorAnnotation = sendToActorAnnotation;
        this.processor = processor;
        ReflectionUtils.makeAccessible(targetMethod);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        try {
            // Invoke the event listener method to get the message
            Object message = ReflectionUtils.invokeMethod(targetMethod, targetBean, event);

            if (message == null) {
                log.debug("Event listener {} returned null, skipping actor message send", targetMethod.getName());
                return;
            }

            // Get actor class and ID from annotation
            Class<? extends SpringActor<?>> actorClass = sendToActorAnnotation.value();
            String actorId = sendToActorAnnotation.actorId();

            if (!StringUtils.hasText(actorId)) {
                actorId = SendToActorBeanPostProcessor.getDefaultActorId(actorClass);
            }

            // Send message to actor
            processor.sendToActor(actorClass, actorId, message);

        } catch (Exception e) {
            log.error("Error processing @SendToActor method {}", targetMethod.getName(), e);
        }
    }

    /**
     * Creates an adapter for the given method.
     *
     * @param targetBean the bean containing the method
     * @param targetMethod the method to adapt
     * @param processor the processor for sending messages
     * @return the adapter, or null if the method is not annotated with @SendToActor
     */
    @Nullable public static SendToActorEventListenerAdapter create(
            Object targetBean, Method targetMethod, SendToActorBeanPostProcessor processor) {
        SendToActor sendToActor = AnnotationUtils.findAnnotation(targetMethod, SendToActor.class);
        if (sendToActor == null) {
            return null;
        }
        return new SendToActorEventListenerAdapter(targetBean, targetMethod, sendToActor, processor);
    }
}
