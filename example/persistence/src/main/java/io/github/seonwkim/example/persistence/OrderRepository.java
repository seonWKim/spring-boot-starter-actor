package io.github.seonwkim.example.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderId(String orderId);

    List<Order> findByCustomerId(String customerId);

    List<Order> findByStatus(OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    List<Order> findByCustomerIdAndStatus(@Param("customerId") String customerId, @Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.amount >= :minAmount ORDER BY o.createdAt DESC")
    List<Order> findHighValueOrders(@Param("minAmount") double minAmount);
}
