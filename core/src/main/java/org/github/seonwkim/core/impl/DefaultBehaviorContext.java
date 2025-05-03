package org.github.seonwkim.core.impl;

import java.util.function.Supplier;

import org.github.seonwkim.core.BehaviorContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultBehaviorContext implements BehaviorContext  {

    private final GenericApplicationContext applicationContext;

    public DefaultBehaviorContext(GenericApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <T> void registerBean(String beanName, Class<T> clazz, Supplier<T> supplier) {
        applicationContext.registerBean(beanName, clazz, supplier);
    }
}
