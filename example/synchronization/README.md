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
- `POST /counter/db/{counterId}/increment` - Increment counter using DB locking
- `GET /counter/db/{counterId}` - Get counter value

### Redis Locking
- `POST /counter/redis/{counterId}/increment` - Increment counter using Redis locking
- `GET /counter/redis/{counterId}` - Get counter value

### Actor Locking
- `POST /counter/actor/{counterId}/increment` - Increment counter using Actor locking
- `GET /counter/actor/{counterId}` - Get counter value

## Synchronization Methods 

### DB Locking 
The DB locking implementation uses JPA with MySQL and pessimistic locking to ensure that only one thread can modify a counter at a time. This approach uses database-level locks to prevent concurrent modifications.

Key components:
- `Counter` entity with JPA annotations
- `CounterRepository` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` annotation
- `DbCounterServiceImpl` with `@Transactional` methods

### Redis Locking 
The Redis locking implementation uses Redisson's distributed locks to ensure that only one thread across multiple application instances can modify a counter at a time. This approach is suitable for distributed applications.

Key components:
- Redisson client for distributed locks
- ReactiveRedisTemplate for counter operations
- Lock acquisition with timeout and automatic release

### Actor Locking 
The Actor locking implementation uses the actor model through spring-boot-starter-actor to handle counter operations. Each counter is represented by a separate actor instance, and all operations on a counter are processed sequentially by its actor, eliminating the need for explicit locks.

Key components:
- `CounterActor` implementing `ShardedActor`
- Entity-per-ID pattern with sharded actors
- Message-based communication for increment and get operations

## Verification

Two verification methods are provided to test the synchronization methods:

### Java Verification Script
A Java-based verification script that tests all three methods by running multiple concurrent increment operations and checking the final counter value.

To run:
```
# Set the spring.profiles.active property
java -jar synchronization.jar --spring.profiles.active=verification
```

### Shell Script Verification
A shell script that uses curl and Apache Bench (ab) to test the REST API endpoints under load.

To run:
```
# Start the application
java -jar synchronization.jar

# In another terminal, run the verification script
./verify.sh
```

## Database Setup

The example uses MySQL. The schema is automatically created using the `schema.sql` file:

```sql
CREATE TABLE IF NOT EXISTS counter (
    counter_id VARCHAR(255) NOT NULL,
    value      BIGINT       NOT NULL,
    version    BIGINT       NOT NULL,
    PRIMARY KEY (counter_id)
)
    ENGINE = InnoDB;
```

## Configuration

The application is configured in `application.yml` with settings for:
- Database connection
- Redis connection
- Actor system configuration
