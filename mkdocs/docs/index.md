# Introduction

Spring Boot Starter Actor is a library that integrates Spring Boot with the actor model using [Pekko](https://pekko.apache.org/) (an
open-source, community-driven fork of Akka).

## What is this project about?

This project bridges the gap between Spring Boot and the actor model, allowing developers to integrate actors to
their applications using familiar Spring Boot patterns with ease.

## Why?

Many use Java with Spring (usually Spring Boot). Modern programming guides recommend building stateless
architectures. But sometimes, stateful features are needed, such as in chat applications where the server needs
to know where clients in the same chatroom are located.

The actor model is a well-known programming model suited for stateful applications:

- Encapsulate logic into actors
- Communicate by sending messages between them

This project aims to bring together the best of both worlds:

- Spring Boot's ease of use and extensive ecosystem
- The actor model's natural approach to encapsulation and state management

## Features

- Auto-configure Pekko with Spring Boot
- Seamless integration with Spring's dependency injection
- Support for both local and cluster modes
- Easy actor creation and management
- Spring-friendly actor references

## Getting Started

Check out the [Getting Started](getting-started.md) guide to begin using Spring Boot Starter Actor in your
projects.

## Examples

Explore our example applications to see Spring Boot Starter Actor in action:

- [**Simple Example**](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/simple): Demonstrates basic actor usage in a non-clustered environment
- [**Cluster Example**](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/cluster): Shows how to use sharded actors in a clustered environment
- [**Chat Example**](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/chat): A complete chat application using actors
- [**Synchronization Example**](https://github.com/seonWKim/spring-boot-starter-actor/tree/main/example/synchronization): Demonstrates synchronization patterns with actors
