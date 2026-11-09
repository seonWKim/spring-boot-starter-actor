# Actor Spring Boot Starter Examples

Welcome to the Actor Spring Boot Starter examples documentation. This documentation provides detailed explanations of the example projects included in the repository, demonstrating various features and use cases of the Actor Spring Boot Starter library.

## Overview

Actor Spring Boot Starter is a library that integrates the Apache Pekko (formerly Akka) actor model with Spring Boot, making it easy to use actors in your Spring applications. The actor model provides a powerful approach to building concurrent, distributed, and resilient applications.

These examples demonstrate how to use Actor Spring Boot Starter in different scenarios, from simple applications to complex, real-world use cases.

!!! info "Learning Path"
    Start with the Simple Example if you're new to the library, then progress through the other examples as you become more comfortable.

## Available Examples

### [Simple Example](simple.md)

The Simple Example demonstrates the basic usage of Actor Spring Boot Starter in a non-clustered environment. It shows how to:

- Create and register actors in a Spring Boot application
- Send messages to actors and receive responses
- Integrate actors with a REST API

**Best for:** Beginners learning the basics of the actor model and Spring Boot integration.

The same example in Kotlin, with `suspend` functions from `actor-kotlin`, is in
[example/kotlin](https://github.com/seonWKim/actor-spring-boot-starter/tree/main/example/kotlin). See the
[Kotlin guide](../guides/kotlin.md).

### [Cluster Example](cluster.md)

The Cluster Example shows how to use Actor Spring Boot Starter in a clustered environment, focusing on sharded actors. It demonstrates:

- Creating and using sharded actors across a cluster
- Distributing actor instances across multiple nodes
- Sending messages to specific entity instances
- Handling entity state in a distributed environment

**Best for:** Understanding distributed actor systems and cluster sharding.

### [Synchronization Example](synchronization.md)

The Synchronization Example demonstrates how to implement efficient synchronization using Actor Spring Boot Starter, comparing it with traditional synchronization approaches. It shows:

- Implementing counters with different synchronization mechanisms
- Comparing database locking, Redis locking, and actor-based synchronization
- Handling concurrent access to shared resources
- Achieving high performance with actor-based synchronization

This example explains why using actors for synchronization is cheap and efficient compared to other approaches.

**Best for:** Understanding performance benefits of actor-based concurrency.

### [Chat Example](chat.md)

The Chat Example demonstrates how to build a real-time chat application using Actor Spring Boot Starter with pub/sub topics. It shows:

- Building a real-time chat application using actors and pub/sub
- Implementing WebSocket communication for real-time messaging
- Creating a scalable, clustered chat system
- Eliminating the need for external message brokers or middleware

This example demonstrates how Actor Spring Boot Starter can be used to build real-world applications efficiently without relying on additional infrastructure components.

**Best for:** Building real-time applications with WebSockets and pub/sub.

### [Monitoring Example](monitoring.md)

The Monitoring example demonstrates the built-in actor metrics module using a minimal Spring Boot application with Prometheus and Micrometer. It shows:

- Adding the metrics agent and library dependency to your project
- Auto-configuring actor metrics collection with zero boilerplate
- Exposing actor lifecycle, message processing, mailbox, and system metrics via Prometheus
- Observing actor restarts, processing times, and throughput

**Best for:** Getting started with actor metrics and Prometheus-based monitoring.

## Getting Started

To run these examples:

1. Clone the repository: `git clone https://github.com/seonWKim/actor-spring-boot-starter.git`
2. Navigate to the example directory you want to run: `cd example/<example-name>`
3. Follow the instructions in the corresponding documentation file

!!! tip "Running Examples"
    Each example includes a README with specific instructions for building and running the application.

## Additional Resources

- [Actor Spring Boot Starter Documentation](../index.md)
- [GitHub Repository](https://github.com/seonWKim/actor-spring-boot-starter)
