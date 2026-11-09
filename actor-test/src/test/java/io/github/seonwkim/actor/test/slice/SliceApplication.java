package io.github.seonwkim.actor.test.slice;

import io.github.seonwkim.actor.EnableActorSupport;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** An application with web, JDBC and H2 on its classpath, one non-actor service and two actors. */
@SpringBootApplication
@EnableActorSupport
public class SliceApplication {}
