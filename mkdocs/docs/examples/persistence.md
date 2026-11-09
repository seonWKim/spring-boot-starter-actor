# Persistence Example

Three ways an actor can keep its state in a database with plain Spring Data JPA: save the current state,
store every change as an event, or save snapshots now and then. Each is one actor per order id.

- **Source:** [`example/persistence`](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/persistence)
- **Runs:** one node, no cluster, H2 in memory
- **Modules:** `actor-spring-boot-starter`, Spring MVC, Spring Data JPA, H2, Actuator

!!! note "Hand-written patterns"
    This example writes the persistence code itself on top of JPA repositories. It does not use the
    starter's `actor-persistence` module. For the module's event-sourced actors with a journal, see
    [Event sourcing](../guides/data/event-sourcing.md).

## What it shows

- `OrderActor`: loads its order when it starts and saves it on every change (JPA entity with `@Version`)
- `EventSourcedOrderActor`: appends each change to an `order_events` table and rebuilds its state by
  replaying the events when it starts
- `SnapshotOrderActor`: saves the state as JSON every 10 changes or 60 seconds, and starts from the latest
  snapshot
- Running actors that block on JDBC on the blocking dispatcher

## Run it

```bash
./gradlew :example:persistence:bootRun
```

The app listens on port 8080. The H2 console is at `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:mem:orderdb`, user `sa`, empty password).

### Current state (`/api/orders`)

```bash
curl -X POST "http://localhost:8080/api/orders/order-1?customerId=customer-1&amount=100.50"
curl -X POST "http://localhost:8080/api/orders/order-1/items?productId=prod-1&quantity=2&price=50.25"
curl -X POST "http://localhost:8080/api/orders/order-1/approve"
curl "http://localhost:8080/api/orders/order-1"
```

### Event sourced (`/api/orders/eventsourced`)

```bash
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2?customerId=customer-2&amount=200.00"
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2/items?productId=prod-2&quantity=3&price=66.67"
curl -X POST "http://localhost:8080/api/orders/eventsourced/order-2/approve?approvedBy=admin"
curl "http://localhost:8080/api/orders/eventsourced/order-2/history"
```

### Snapshots (`/api/orders/snapshot`)

```bash
curl -X POST "http://localhost:8080/api/orders/snapshot/order-3?customerId=customer-3&amount=150.00"
curl -X PUT "http://localhost:8080/api/orders/snapshot/order-3?amount=175.00"
curl "http://localhost:8080/api/orders/snapshot/order-3"
curl -X POST "http://localhost:8080/api/orders/snapshot/order-3/save-snapshot"
```

## Walkthrough

### Blocking work goes on the blocking dispatcher

JPA calls block their thread until the database answers. The controller spawns these actors with
`withBlockingDispatcher()`, so they never hold the default dispatcher's threads that every other actor
shares:

=== "Java"

    ```java
    private <A extends SpringActorWithContext<C, ?>, C> CompletionStage<SpringActorHandle<C>> onBlockingDispatcher(
            Class<A> actorClass, String orderId) {
        return actorSystem
                .actor(actorClass)
                .withId(orderId)
                .withBlockingDispatcher()
                .getOrSpawn();
    }
    ```

=== "Kotlin"

    ```kotlin
    // Call from a suspend function or a coroutine.
    suspend fun order(orderId: String): SpringActorHandle<OrderActor.Command> =
        actorSystem.actor<OrderActor, _>()
            .withId(orderId)
            .withBlockingDispatcher()
            .awaitGetOrSpawn()
    ```

Each endpoint then asks the actor with a 5-second timeout.

### Current state: `OrderActor`

The actor gets its repository by constructor injection, loads the order in `withState`, and saves after
each change:

```java
@Override
public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
    return SpringActorBehavior.builder(Command.class, actorContext)
            .withState(ctx -> {
                Order order = orderRepository.findByOrderId(actorContext.actorId()).orElse(null);
                return new OrderActorBehavior(ctx, actorContext, orderRepository, order);
            })
            .onMessage(CreateOrder.class, OrderActorBehavior::handleCreateOrder)
            .onMessage(AddItem.class, OrderActorBehavior::handleAddItem)
            .onMessage(GetOrder.class, OrderActorBehavior::handleGetOrder)
            .onMessage(ApproveOrder.class, OrderActorBehavior::handleApproveOrder)
            .build();
}
```

Because one actor handles all writes for an order, writes for the same order never race. `@Version` on
`Order` still catches a write from outside the actor (`OptimisticLockingFailureException`).

### Event sourcing: `EventSourcedOrderActor`

Each command appends a domain event (`OrderCreatedEvent`, `OrderItemAddedEvent`, `OrderApprovedEvent`,
`OrderCancelledEvent`) as JSON with a sequence number. On start, the actor replays them:

```java
private OrderState rebuildState(String orderId) {
    List<OrderEvent> events = eventRepository.findByOrderIdOrderBySequenceNumberAsc(orderId);
    OrderState state = new OrderState(orderId);
    for (OrderEvent event : events) {
        state.apply(deserializeEvent(event)); // the real code wraps this in try/catch
    }
    return state;
}
```

`/history` returns the stored events, the order's full audit trail.

### Snapshots: `SnapshotOrderActor`

On start, the actor reads the latest snapshot (or the order row). After each change it asks a
`SnapshotStrategy` whether to save one:

```java
SnapshotStrategy strategy = new HybridSnapshotStrategy(
        10,     // every 10 operations
        60000); // or every 60 seconds
```

After saving, it deletes snapshots older than an hour once there are more than five.

## Things to keep in mind

- The database is in memory: everything is gone when the app stops. `build.gradle.kts` also includes
  the PostgreSQL driver; point `spring.datasource.*` at PostgreSQL to keep the data.
- Each actor here is a local actor. On a cluster, make them sharded actors so one node owns each order.

## Try next

- Restart the app with a file-based H2 URL (`jdbc:h2:file:./orderdb`) and check that each actor reloads
  its order.
- Add a `RejectOrder` endpoint for `OrderActor` (the actor already handles it).
- Compare with the starter's own event sourcing in [Event sourcing](../guides/data/event-sourcing.md).

## Related

- [State in a database](../guides/data/state-in-a-database.md)
- [Event sourcing](../guides/data/event-sourcing.md) and [Projections](../guides/data/projections.md)
- [Dispatchers](../guides/core/dispatchers.md): the blocking dispatcher
