package io.github.seonwkim.actor.projection;

/** When a {@link SpringProjection} stores its offset, and so how often an event may be handled. */
public enum Delivery {
    /**
     * The offset is stored in the handler's transaction: an event's writes and its offset commit or
     * roll back together, so each event is applied once. For writes to the application's database
     * (Spring Data repositories, {@code JdbcTemplate}).
     */
    EXACTLY_ONCE,
    /**
     * The offset is stored after the handler, in batches: after a crash the last events are handled
     * again. For writes outside the database, such as {@code KafkaTemplate.send(...).get()}; make them
     * idempotent (for example keyed by persistence id and sequence number).
     */
    AT_LEAST_ONCE
}
