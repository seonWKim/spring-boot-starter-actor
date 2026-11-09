package io.github.seonwkim.actor.projection;

import java.time.Instant;

/**
 * An event read by a {@link SpringProjection}.
 *
 * @param persistenceId the id of the actor that stored it ({@code PersistenceId.id()}, such as
 *     {@code Account|alice})
 * @param sequenceNumber its number among that actor's events, from 1
 * @param timestamp when it was stored
 * @param event the event
 */
public record ProjectedEvent<E>(String persistenceId, long sequenceNumber, Instant timestamp, E event) {}
