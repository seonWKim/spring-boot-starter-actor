package io.github.seonwkim.core.event;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorSystem;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Bean post processor that detects methods annotated with {@link SendToActor}
 * and wraps them to send messages to actors.
 *
 * <p>This processor scans for methods that are annotated with both
 * {@code @EventListener} and {@code @SendToActor}, and automatically forwards
 * the result of the event listener to the specified actor.
 *
 * <p>The processor works by:
 * <ol>
 *   <li>Detecting beans with {@code @SendToActor} annotated methods</li>
 *   <li>Creating proxies that intercept event listener invocations</li>
 *   <li>Spawning or getting the target actor</li>
 *   <li>Sending the returned message to the actor</li>
 * </ol>
 */
public class SendToActorBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(SendToActorBeanPostProcessor.class);

    private final SpringActorSystem actorSystem;

    @Nullable private ApplicationContext applicationContext;

    public SendToActorBeanPostProcessor(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Nullable public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    @Nullable public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        ReflectionUtils.doWithMethods(
                targetClass,
                method -> processMethod(bean, method),
                method -> AnnotationUtils.findAnnotation(method, SendToActor.class) != null);

        return bean;
    }

    private void processMethod(Object bean, Method method) {
        SendToActor sendToActor = AnnotationUtils.findAnnotation(method, SendToActor.class);
        EventListener eventListener = AnnotationUtils.findAnnotation(method, EventListener.class);

        if (sendToActor == null) {
            return;
        }

        if (eventListener == null) {
            log.warn(
                    "@SendToActor annotation on method {} should be used with @EventListener",
                    method.getName());
            return;
        }

        log.debug(
                "Detected @SendToActor on method {} targeting actor {}",
                method.getName(),
                sendToActor.value().getSimpleName());

        // The actual forwarding is handled by SendToActorEventListenerAdapter
        // which is created by the EventBridgeAutoConfiguration
    }

    /**
     * Sends a message to an actor asynchronously.
     *
     * @param actorClass the actor class
     * @param actorId the actor ID
     * @param message the message to send
     * @return a future that completes when the message is sent
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CompletableFuture<Void> sendToActor(
            Class<? extends SpringActor<?>> actorClass, String actorId, Object message) {
        if (message == null) {
            log.debug("Event listener returned null, skipping actor message send");
            return CompletableFuture.completedFuture(null);
        }

        return actorSystem
                .getOrSpawn((Class) actorClass, actorId)
                .thenAccept(actorRef -> {
                    ((io.github.seonwkim.core.SpringActorRef) actorRef).tell(message);
                    log.debug(
                            "Sent message {} to actor {} with id {}",
                            message.getClass().getSimpleName(),
                            actorClass.getSimpleName(),
                            actorId);
                })
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    log.error(
                            "Failed to send message to actor {} with id {}",
                            actorClass.getSimpleName(),
                            actorId,
                            throwable);
                    return null;
                });
    }

    /**
     * Generates a default actor ID from the actor class name.
     *
     * @param actorClass the actor class
     * @return the default actor ID
     */
    public static String getDefaultActorId(Class<? extends SpringActor<?>> actorClass) {
        String simpleName = actorClass.getSimpleName();
        return StringUtils.uncapitalize(simpleName);
    }
}
