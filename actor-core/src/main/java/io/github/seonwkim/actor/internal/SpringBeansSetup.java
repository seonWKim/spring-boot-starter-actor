package io.github.seonwkim.actor.internal;

import org.apache.pekko.actor.setup.Setup;
import org.springframework.beans.factory.BeanFactory;

/** Gives Pekko extensions and dispatcher configurators the application's beans. */
public final class SpringBeansSetup extends Setup {

    private final BeanFactory beanFactory;

    public SpringBeansSetup(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public BeanFactory beanFactory() {
        return beanFactory;
    }
}
