package io.github.seonwkim.actor.test.cluster;

import io.github.seonwkim.actor.EnableActorSupport;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The application every node of the helper's cluster runs. */
@SpringBootApplication
@EnableActorSupport
public class ClusterApplication {}
