package io.github.seonwkim.example.counter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

/**
 * Repository for accessing Counter entities in the database.
 * Provides methods for finding, saving, and locking counters.
 */
public interface CounterRepository extends JpaRepository<Counter, String> {

    /**
     * Finds a counter by its ID using a native SQL query with a FOR UPDATE lock.
     * This ensures that the row is locked for writing during the transaction.
     *
     * @param counterId The ID of the counter to lock
     * @return An Optional containing the counter if found
     */
    @Query(
            value = "SELECT * FROM counter WHERE counter_id = :counterId FOR UPDATE",
            nativeQuery = true
    )
    Optional<Counter> findByIdWithLock(@Param("counterId") String counterId);
}
