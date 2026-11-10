package io.github.seonwkim.example.persistence;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.example.persistence.eventlog.EventLogOrderActor;
import io.github.seonwkim.example.persistence.snapshot.SnapshotOrderActor;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final SpringActorSystem actorSystem;

    public OrderController(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * These actors call JPA repositories, which block their thread until the database answers, so they
     * run on the blocking dispatcher and never on the default one that every other actor shares.
     */
    private <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> onBlockingDispatcher(
            Class<A> actorClass, String orderId) {
        return actorSystem
                .actor(actorClass)
                .withId(orderId)
                .withBlockingDispatcher()
                .getOrSpawn();
    }

    // JPA-based order endpoints
    @PostMapping("/{orderId}")
    public CompletionStage<OrderActor.OrderResponse> createOrder(
            @PathVariable String orderId, @RequestParam String customerId, @RequestParam double amount) {

        return onBlockingDispatcher(OrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new OrderActor.CreateOrder(customerId, amount))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PostMapping("/{orderId}/items")
    public CompletionStage<OrderActor.OrderResponse> addItem(
            @PathVariable String orderId,
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam double price) {

        return onBlockingDispatcher(OrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new OrderActor.AddItem(productId, quantity, price))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PostMapping("/{orderId}/approve")
    public CompletionStage<OrderActor.OrderResponse> approveOrder(@PathVariable String orderId) {
        return onBlockingDispatcher(OrderActor.class, orderId).thenCompose(ref -> ref.ask(new OrderActor.ApproveOrder())
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }

    @GetMapping("/{orderId}")
    public CompletionStage<OrderActor.OrderResponse> getOrder(@PathVariable String orderId) {
        return onBlockingDispatcher(OrderActor.class, orderId).thenCompose(ref -> ref.ask(new OrderActor.GetOrder())
                .withTimeout(Duration.ofSeconds(5))
                .execute());
    }

    // Event-log order endpoints
    @PostMapping("/eventlog/{orderId}")
    public CompletionStage<EventLogOrderActor.OrderResponse> createEventLogOrder(
            @PathVariable String orderId, @RequestParam String customerId, @RequestParam double amount) {

        return onBlockingDispatcher(EventLogOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new EventLogOrderActor.CreateOrder(customerId, amount))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PostMapping("/eventlog/{orderId}/items")
    public CompletionStage<EventLogOrderActor.OrderResponse> addItemToEventLogOrder(
            @PathVariable String orderId,
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam double price) {

        return onBlockingDispatcher(EventLogOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new EventLogOrderActor.AddItem(productId, quantity, price))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PostMapping("/eventlog/{orderId}/approve")
    public CompletionStage<EventLogOrderActor.OrderResponse> approveEventLogOrder(
            @PathVariable String orderId, @RequestParam String approvedBy) {

        return onBlockingDispatcher(EventLogOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new EventLogOrderActor.ApproveOrder(approvedBy))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @GetMapping("/eventlog/{orderId}/history")
    public CompletionStage<EventLogOrderActor.HistoryResponse> getOrderHistory(@PathVariable String orderId) {

        return onBlockingDispatcher(EventLogOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new EventLogOrderActor.GetHistory())
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    // Snapshot-based order endpoints
    @PostMapping("/snapshot/{orderId}")
    public CompletionStage<SnapshotOrderActor.OrderResponse> createSnapshotOrder(
            @PathVariable String orderId, @RequestParam String customerId, @RequestParam double amount) {

        return onBlockingDispatcher(SnapshotOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new SnapshotOrderActor.CreateOrder(customerId, amount))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PutMapping("/snapshot/{orderId}")
    public CompletionStage<SnapshotOrderActor.OrderResponse> updateSnapshotOrder(
            @PathVariable String orderId, @RequestParam double amount) {

        return onBlockingDispatcher(SnapshotOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new SnapshotOrderActor.UpdateOrder(amount))
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @GetMapping("/snapshot/{orderId}")
    public CompletionStage<SnapshotOrderActor.OrderResponse> getSnapshotOrder(@PathVariable String orderId) {
        return onBlockingDispatcher(SnapshotOrderActor.class, orderId)
                .thenCompose(ref -> ref.ask(new SnapshotOrderActor.GetOrder())
                        .withTimeout(Duration.ofSeconds(5))
                        .execute());
    }

    @PostMapping("/snapshot/{orderId}/save-snapshot")
    public CompletionStage<Void> saveSnapshot(@PathVariable String orderId) {
        return onBlockingDispatcher(SnapshotOrderActor.class, orderId)
                .thenAccept(ref -> ref.tell(new SnapshotOrderActor.SaveSnapshot()));
    }
}
