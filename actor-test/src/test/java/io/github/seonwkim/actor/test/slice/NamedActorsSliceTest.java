package io.github.seonwkim.actor.test.slice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.test.ActorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/** Naming actors keeps only those. */
@ActorTest(GreeterActor.class)
class NamedActorsSliceTest {

    @Autowired
    ApplicationContext context;

    @Test
    void startsOnlyTheNamedActors() {
        assertThat(context.getBeanNamesForType(GreeterActor.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(CounterActor.class)).isEmpty();
    }
}
