package io.github.seonwkim.actor.test.slice;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.test.ActorTest;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/** The slice starts the actor system and the actors, and nothing else of the application. */
@ActorTest(properties = "spring.actor.name=slice-test")
class ActorTestSliceTest {

    @Autowired
    SpringActorSystem actorSystem;

    @Autowired
    ApplicationContext context;

    @Test
    void startsTheActorSystemAndTheActorsFromTheApplication() throws Exception {
        String reply = actorSystem
                .actor(GreeterActor.class)
                .withId("greeter")
                .spawnAndWait()
                .ask(new GreeterActor.Greet("slice"))
                .withTimeout(Duration.ofSeconds(5))
                .execute()
                .toCompletableFuture()
                .get();

        assertThat(reply).isEqualTo("hello slice");
        assertThat(actorSystem.getName()).isEqualTo("slice-test");
        assertThat(context.getBeanNamesForType(CounterActor.class)).hasSize(1);
    }

    @Test
    void leavesOutWebDataAndOtherComponents() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DispatcherServlet.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ServletWebServerFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GreetingService.class)).isEmpty();
    }
}
