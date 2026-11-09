package io.github.seonwkim.actor.projection.internal;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.ClusterSupport;
import io.github.seonwkim.actor.projection.Delivery;
import io.github.seonwkim.actor.projection.ProjectedEvent;
import io.github.seonwkim.actor.projection.SpringProjection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.persistence.jdbc.query.javadsl.JdbcReadJournal;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.projection.Projection;
import org.apache.pekko.projection.ProjectionBehavior;
import org.apache.pekko.projection.ProjectionId;
import org.apache.pekko.projection.eventsourced.EventEnvelope;
import org.apache.pekko.projection.eventsourced.javadsl.EventSourcedProvider;
import org.apache.pekko.projection.javadsl.SourceProvider;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;
import org.apache.pekko.projection.jdbc.javadsl.JdbcProjection;
import org.springframework.transaction.PlatformTransactionManager;

/** Runs {@link SpringProjection}s as Pekko JDBC projections over the events of their tag. */
public final class PekkoProjections {

    private PekkoProjections() {}

    /**
     * Starts {@code projection}: on this node, or in cluster mode as a cluster singleton, so one node
     * handles its events.
     */
    public static <E> void start(
            SpringActorSystem actorSystem,
            SpringProjection<E> projection,
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        ActorSystem<?> system = actorSystem.unwrap(ActorSystem.class);
        Behavior<ProjectionBehavior.Command> behavior =
                ProjectionBehavior.create(create(system, projection, dataSource, transactionManager));
        String name = "projection-" + URLEncoder.encode(projection.name(), StandardCharsets.UTF_8);
        if (actorSystem.isClusterMode()) {
            ClusterSupport.require().spawnSingleton(system, behavior, name, Props.empty());
        } else {
            system.systemActorOf(behavior, name, Props.empty());
        }
    }

    private static <E> Projection<EventEnvelope<E>> create(
            ActorSystem<?> system,
            SpringProjection<E> projection,
            DataSource dataSource,
            PlatformTransactionManager transactionManager) {
        ProjectionId id = ProjectionId.of(projection.name(), projection.tag());
        SourceProvider<Offset, EventEnvelope<E>> events =
                EventSourcedProvider.eventsByTag(system, JdbcReadJournal.Identifier(), projection.tag());
        Supplier<SpringTransactionSession> sessions =
                () -> new SpringTransactionSession(transactionManager, dataSource);
        Supplier<JdbcHandler<EventEnvelope<E>, SpringTransactionSession>> handler = () -> new Handler<>(projection);
        return projection.delivery() == Delivery.EXACTLY_ONCE
                ? JdbcProjection.exactlyOnce(id, events, sessions, handler, system)
                : JdbcProjection.atLeastOnce(id, events, sessions, handler, system);
    }

    private static final class Handler<E> extends JdbcHandler<EventEnvelope<E>, SpringTransactionSession> {
        private final SpringProjection<E> projection;

        Handler(SpringProjection<E> projection) {
            this.projection = projection;
        }

        @Override
        public void process(SpringTransactionSession session, EventEnvelope<E> envelope) throws Exception {
            projection.handle(new ProjectedEvent<>(
                    envelope.persistenceId(),
                    envelope.sequenceNr(),
                    Instant.ofEpochMilli(envelope.timestamp()),
                    envelope.event()));
        }
    }
}
