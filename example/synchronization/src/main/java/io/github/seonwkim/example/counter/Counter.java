package io.github.seonwkim.example.counter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * JPA entity representing a counter in the database.
 * Uses optimistic locking with a version column.
 */
@Entity
@Table(name = "counter")
public class Counter {

    @Id
    @Column(name = "counter_id", nullable = false)
    private String counterId;

    @Column(name = "value", nullable = false)
    private long value;

    @Version
    @Column(name = "version")
    private long version;

    /**
     * Default constructor required by JPA.
     */
    public Counter() {
    }

    /**
     * Creates a new counter with the given ID and initial value.
     *
     * @param counterId The ID of the counter
     * @param value The initial value of the counter
     */
    public Counter(String counterId, long value) {
        this.counterId = counterId;
        this.value = value;
    }

    /**
     * Gets the counter ID.
     *
     * @return The counter ID
     */
    public String getCounterId() {
        return counterId;
    }

    /**
     * Sets the counter ID.
     *
     * @param counterId The counter ID
     */
    public void setCounterId(String counterId) {
        this.counterId = counterId;
    }

    /**
     * Gets the counter value.
     *
     * @return The counter value
     */
    public long getValue() {
        return value;
    }

    /**
     * Sets the counter value.
     *
     * @param value The counter value
     */
    public void setValue(long value) {
        this.value = value;
    }

    /**
     * Gets the version.
     *
     * @return The version
     */
    public long getVersion() {
        return version;
    }

    /**
     * Sets the version.
     *
     * @param version The version
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Increments the counter value by 1.
     *
     * @return The new counter value
     */
    public long increment() {
        return ++value;
    }
}
