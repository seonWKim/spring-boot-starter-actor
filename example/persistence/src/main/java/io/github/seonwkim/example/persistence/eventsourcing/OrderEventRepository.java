package io.github.seonwkim.example.persistence.eventsourcing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderIdOrderBySequenceNumberAsc(String orderId);

    @Query("SELECT MAX(e.sequenceNumber) FROM OrderEvent e WHERE e.orderId = :orderId")
    Long findMaxSequenceNumber(@Param("orderId") String orderId);

    List<OrderEvent> findByOrderIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
        String orderId, long sequenceNumber);
}
