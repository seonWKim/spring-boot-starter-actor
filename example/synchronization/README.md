# Synchronization Example 

An example demonstrating different synchronization methods for incrementing a counter value. This example shows how to implement and compare three different approaches to handling concurrent modifications to shared state.

## Overview

This example implements a simple counter API with three different synchronization methods:
1. Database locking (using MySQL)
2. Redis distributed locking
3. Actor-based synchronization (using spring-boot-starter-actor)

Each method provides the same functionality but uses different techniques to ensure data consistency under concurrent access.

## API Endpoints

The following REST endpoints are available:

### DB Locking
- `GET /counter/db/{counterId}/increment` - Increment counter using DB locking
- `GET /counter/db/{counterId}` - Get counter value

### Redis Locking
- `GET /counter/redis/{counterId}/increment` - Increment counter using Redis locking
- `GET /counter/redis/{counterId}` - Get counter value

### Actor Locking
- `GET /counter/actor/{counterId}/increment` - Increment counter using Actor locking
- `GET /counter/actor/{counterId}` - Get counter value

## Synchronization Methods 

### DB Locking 
The DB locking implementation uses JPA with MySQL and pessimistic locking to ensure that only one thread can modify a counter at a time. This approach uses database-level locks to prevent concurrent modifications.

Key components:
- `Counter` entity with JPA annotations
- `CounterRepository` with native SQL query using `FOR UPDATE` clause
- `DbCounterService` with `CustomTransactionTemplate` for transaction management

### Redis Locking 
The Redis locking implementation uses Spring Data Redis's ReactiveRedisTemplate to ensure that only one thread across multiple application instances can modify a counter at a time. This approach is suitable for distributed applications.

Key components:
- ReactiveRedisTemplate for counter operations and locking
- Lock acquisition using setIfAbsent (SETNX) with timeout
- Retry mechanism with backoff for lock acquisition
- Automatic lock release after operation completion

### Actor Locking 
The Actor locking implementation uses the actor model through spring-boot-starter-actor to handle counter operations. Each counter is represented by a separate actor instance, and all operations on a counter are processed sequentially by its actor, eliminating the need for explicit locks.

Key components:
- `CounterActor` implementing `ShardedActor`
- Entity-per-ID pattern with sharded actors
- Message-based communication for increment and get operations

## Verification
A shell script that uses curl and Apache Bench (ab) to test the REST API endpoints under load.

To run:
```
# In the root dir, start the application
$ sh cluster-start.sh synchronization io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3 

# In another terminal, run the verification script
$ cd example/synchronization 
$ ./verify.sh
```

## Database Setup

The example uses MySQL. Refer to the `schema.sql` file:

```sql
CREATE TABLE IF NOT EXISTS counter (
    counter_id VARCHAR(255) NOT NULL,
    value      BIGINT       NOT NULL,
    PRIMARY KEY (counter_id)
)
    ENGINE = InnoDB;
```

## Configuration

The application is configured in `application.yml` with settings for:
- Database connection
- Redis connection
- Actor system configuration
