package io.github.seonwkim.actor.test;

import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.test.context.TestContextAnnotationUtils;

/** Adds {@link ActorTest#properties()} to the test's environment. */
class ActorTestContextBootstrapper extends SpringBootTestContextBootstrapper {

    @Override
    protected String[] getProperties(Class<?> testClass) {
        ActorTest annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, ActorTest.class);
        return annotation == null ? new String[0] : annotation.properties();
    }
}
