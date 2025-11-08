# Spring Boot Starter Actor Examples

Welcome to the Spring Boot Starter Actor examples documentation. This documentation provides detailed explanations of the example projects included in the repository, demonstrating various features and use cases of the Spring Boot Starter Actor library.

## Overview

Spring Boot Starter Actor is a library that integrates the Apache Pekko (formerly Akka) actor model with Spring Boot, making it easy to use actors in your Spring applications. The actor model provides a powerful approach to building concurrent, distributed, and resilient applications.

These examples demonstrate how to use Spring Boot Starter Actor in different scenarios, from simple applications to complex, real-world use cases.

## Available Examples

### [Simple Example](simple.md)

The Simple Example demonstrates the basic usage of Spring Boot Starter Actor in a non-clustered environment. It shows how to:

- Create and register actors in a Spring Boot application
- Send messages to actors and receive responses
- Integrate actors with a REST API

This is a great starting point for understanding the core concepts of the library.

### [Cluster Example](cluster.md)

The Cluster Example shows how to use Spring Boot Starter Actor in a clustered environment, focusing on how entities can be easily used with the library. It demonstrates:

- Creating and using sharded actors across a cluster
- Distributing actor instances across multiple nodes
- Sending messages to specific entity instances
- Handling entity state in a distributed environment

### [Synchronization Example](synchronization.md)

The Synchronization Example demonstrates how to implement efficient synchronization using Spring Boot Starter Actor, comparing it with traditional synchronization approaches. It shows:

- Implementing counters with different synchronization mechanisms
- Comparing database locking, Redis locking, and actor-based synchronization
- Handling concurrent access to shared resources
- Achieving high performance with actor-based synchronization

This example explains why using actors for synchronization is cheap and efficient compared to other approaches.

### [Chat Example](chat.md)

The Chat Example demonstrates how to build a real-time chat application using Spring Boot Starter Actor without introducing third-party middleware. It shows:

- Building a real-time chat application using actors
- Implementing WebSocket communication for real-time messaging
- Creating a scalable, clustered chat system
- Eliminating the need for external message brokers or middleware

This example demonstrates how Spring Boot Starter Actor can be used to build real-world applications efficiently without relying on additional infrastructure components.

### [Supervision Example](supervision.md)

The Supervision Example demonstrates how to build hierarchical actor supervision systems with real-time visualization. It shows:

- Building hierarchical actor systems with arbitrary depth
- Implementing different supervision strategies (restart, stop, resume)
- Visualizing actor hierarchies in real-time with an interactive web UI
- Tracking actor failures and monitoring supervision behavior
- Creating fault-tolerant systems using the actor model

This example provides insights into how supervision trees can be used to build resilient, self-healing systems with clear failure boundaries.

### [Monitoring](monitoring.md)

The Monitoring example demonstrates how to monitor and analyze your actor system's performance. It shows:

- Setting up a complete monitoring stack with Prometheus and Grafana
- Collecting and exporting metrics from your actor system
- Visualizing actor performance metrics in real-time
- Tracking message processing times and throughput
- Monitoring cluster health and resource usage

This example provides insights into how Spring Boot Starter Actor can be used to observe and optimize your application's performance without complex configuration.

## Getting Started

To run these examples:

1. Clone the repository
2. Navigate to the example directory you want to run
3. Follow the instructions in the corresponding documentation file

## Additional Resources

- [Spring Boot Starter Actor Documentation](../index.md)
- [API Reference](../api-reference.md)
- [GitHub Repository](https://github.com/seonwkim/spring-boot-starter-actor)
