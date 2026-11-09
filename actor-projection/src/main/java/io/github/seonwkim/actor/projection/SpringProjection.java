package io.github.seonwkim.actor.projection;

/**
 * Reads the events of one tag, in the order they were stored, and hands each to {@link #handle}. Its
 * offset (the last event it handled) is stored on the application's {@code DataSource}, so after a
 * restart it resumes where it stopped. Declare it as a bean; it starts with the actor system.
 *
 * <pre>{@code
 * @Component
 * public class BalanceProjection implements SpringProjection<AccountActor.Event> {
 *     private final BalanceRepository balances;
 *
 *     public String name() { return "balances"; }
 *     public String tag() { return "accounts"; }
 *
 *     public void handle(ProjectedEvent<AccountActor.Event> event) {
 *         if (event.event() instanceof AccountActor.Deposited deposited) {
 *             balances.add(event.persistenceId(), deposited.amount());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <E> the event type of the tagged events
 */
public interface SpringProjection<E> {

    /** The projection's name. Its offset is stored under it: a new name starts from the first event. */
    String name();

    /** The tag of the events it reads, as set by {@code SpringEventSourcedBehavior.Builder.withTagger}. */
    String tag();

    /** When the offset is stored. */
    default Delivery delivery() {
        return Delivery.EXACTLY_ONCE;
    }

    /**
     * Handles one event. It runs in a Spring transaction on the application's {@code DataSource}:
     * with {@link Delivery#EXACTLY_ONCE} the offset is stored in the same transaction. When it throws,
     * the transaction rolls back and the projection restarts after a backoff, from the stored offset.
     */
    void handle(ProjectedEvent<E> event) throws Exception;
}
