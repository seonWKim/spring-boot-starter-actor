package org.github.seonwkim.core;

import java.util.function.Supplier;

public interface BehaviorContext {
    <T> void registerBean(String beanName, Class<T> clazz, Supplier<T> supplier);
}
