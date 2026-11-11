package io.github.seonwkim.actor.autoconfigure;

import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Checks each actor bean as Spring creates it ({@code spring.actor.validation.actor-beans}).
 *
 * <ul>
 *   <li><b>Mutable fields.</b> One bean serves every actor of its class, so an instance field that is
 *       neither {@code static} nor {@code final} is state shared by all of them and their threads.
 *       Fields Spring injects are wiring, not state: annotated with {@code @Autowired}, {@code @Value},
 *       {@code @Inject} or {@code @Resource}, set through a setter with one of those annotations, or
 *       set as a property of the bean definition. Synthetic fields (a compiler's {@code this$0}) are
 *       skipped.
 *   <li><b>Proxies.</b> A Spring AOP proxy's advice never reaches the handlers, which run in the
 *       behavior that {@code create} returns.
 * </ul>
 *
 * <p>The fields are read from the class the user declared, not from a proxy or CGLIB subclass. The
 * post-processor is deliberately not {@code Ordered}: Spring runs unordered post-processors after the
 * ordered ones, so it sees the proxy that the AOP post-processors return.
 */
final class ActorBeanValidator implements BeanPostProcessor, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(ActorBeanValidator.class);

    static final String GUIDE =
            "https://seonwkim.github.io/actor-spring-boot-starter/guides/core/actors/#what-not-to-do-in-an-actor-bean";

    /** Annotations by which Spring injects a field or a setter. Names, so optional ones need not be present. */
    private static final List<String> INJECTION_ANNOTATIONS = List.of(
            "org.springframework.beans.factory.annotation.Autowired",
            "org.springframework.beans.factory.annotation.Value",
            "jakarta.inject.Inject",
            "javax.inject.Inject",
            "jakarta.annotation.Resource",
            "javax.annotation.Resource");

    private final ActorProperties.Validation.Mode mode;

    @Nullable private ConfigurableListableBeanFactory beanFactory;

    ActorBeanValidator(ActorProperties.Validation.Mode mode) {
        this.mode = mode;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (beanFactory instanceof ConfigurableListableBeanFactory configurable) {
            this.beanFactory = configurable;
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (mode == ActorProperties.Validation.Mode.OFF
                || !(bean instanceof SpringActorWithContext<?, ?> || bean instanceof SpringShardedActor<?>)) {
            return bean;
        }
        Class<?> userClass = ClassUtils.getUserClass(AopProxyUtils.ultimateTargetClass(bean));
        String actor = "Actor bean '" + beanName + "' (" + userClass.getName() + ")";
        List<String> problems = new ArrayList<>();
        if (AopUtils.isAopProxy(bean)) {
            problems.add(actor + " is a Spring AOP proxy: its advice (@Transactional, @Async, @Cacheable, ...)"
                    + " does not apply to the message handlers, which run in the behavior that create() returns."
                    + " Put such annotations on a separate service the handler calls.");
        }
        for (Field field : mutableFields(userClass, beanName)) {
            problems.add(actor + " has the mutable field '" + field.getName() + "' (declared in "
                    + field.getDeclaringClass().getSimpleName() + "): every actor of this class shares it"
                    + " across threads. Keep per-actor state in the behavior (withState), or make the field final.");
        }
        if (problems.isEmpty()) {
            return bean;
        }
        if (mode == ActorProperties.Validation.Mode.FAIL) {
            throw new IllegalStateException(String.join("\n", problems)
                    + "\nSee " + GUIDE + ". spring.actor.validation.actor-beans=warn only logs these.");
        }
        problems.forEach(problem -> log.warn("{} See {}", problem, GUIDE));
        return bean;
    }

    private List<Field> mutableFields(Class<?> userClass, String beanName) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = userClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers)
                        && !Modifier.isFinal(modifiers)
                        && !field.isSynthetic()
                        && !isInjected(field, userClass, beanName)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private boolean isInjected(Field field, Class<?> userClass, String beanName) {
        return hasInjectionAnnotation(field)
                || hasInjectedSetter(field, userClass)
                || (beanFactory != null
                        && beanFactory.containsBeanDefinition(beanName)
                        && beanFactory
                                .getMergedBeanDefinition(beanName)
                                .getPropertyValues()
                                .contains(field.getName()));
    }

    private static boolean hasInjectedSetter(Field field, Class<?> userClass) {
        String setter = "set" + StringUtils.capitalize(field.getName());
        for (Class<?> type = userClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(setter)
                        && method.getParameterCount() == 1
                        && hasInjectionAnnotation(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInjectionAnnotation(AnnotatedElement element) {
        MergedAnnotations annotations = MergedAnnotations.from(element);
        return INJECTION_ANNOTATIONS.stream().anyMatch(annotations::isPresent);
    }
}
