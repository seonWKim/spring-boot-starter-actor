package io.github.seonwkim.actor.internal;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.SpringFactoriesLoader;

/** The native image hints of every registrar in {@code META-INF/spring/aot.factories}, as Spring AOT collects them. */
public final class AotHints {

    private AotHints() {}

    public static RuntimeHints collect() {
        RuntimeHints hints = new RuntimeHints();
        ClassLoader classLoader = AotHints.class.getClassLoader();
        SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories", classLoader)
                .load(RuntimeHintsRegistrar.class)
                .forEach(registrar -> registrar.registerHints(hints, classLoader));
        return hints;
    }
}
