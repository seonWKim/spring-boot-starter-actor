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
@Repository
public interface CounterRepository extends JpaRepository<Counter, String> {

    /**
     * Finds a counter by its ID with a pessimistic write lock.
     * This ensures that only one thread can access the counter at a time.
     *
     * @param counterId The ID of the counter to find
     * @return An Optional containing the counter if found, or empty if not found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Counter c WHERE c.counterId = :counterId")
    Optional<Counter> findByIdWithLock(@Param("counterId") String counterId);
}
