<img src="mkdocs/docs/logo.png" alt="Library Logo" width="200"/>

# Spring Boot Starter Actor

A library that integrates Spring Boot with the actor model using [Pekko](https://pekko.apache.org/) (an
open-source, community-driven fork of Akka).

<div style="border: 2px solid #ccc; display: inline-block; border-radius: 8px; overflow: hidden;">
  <img src="mkdocs/docs/chat.gif" alt="Demo"/>
</div>


## Documentation

For comprehensive documentation, visit our [Documentation Site](https://seonwkim.github.io/spring-boot-starter-actor/).

## Core Concepts

This project bridges the gap between Spring Boot and the actor model, allowing developers to build stateful
applications using familiar Spring Boot patterns while leveraging the power of the actor model for managing
state and concurrency.

The actor model is a programming paradigm that:
- Encapsulates logic and state into actors
- Communicates by sending messages between actors
- Provides natural isolation and concurrency control

## Key Features

- Auto-configure Pekko with Spring Boot
- Seamless integration with Spring's dependency injection
- Support for both local and cluster modes
- Easy actor creation and management
- Spring-friendly actor references

## Quick Start

### Prerequisites

- Java 11 or higher
- Spring Boot 2.x or 3.x

### Installation

Add the dependency to your project:

```gradle
// Manually overwrite spring managed jackson dependency 
dependencyManagement {
	imports {
		// pekko-serialization-jackson_3 require minimum 2.17.3 version of jackson
		mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
	}
}

// Gradle (Spring Boot 2.7.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.0.14'

// Gradle (Spring Boot 3.2.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.0.14'
```

```xml
<dependencyManagement>
  <dependencies>
    <!-- Override Spring Boot's jackson-bom with 2.17.3 -->
    <dependency>
      <groupId>com.fasterxml.jackson</groupId>
      <artifactId>jackson-bom</artifactId>
      <version>2.17.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- Maven (Spring Boot 2.7.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor</artifactId>
  <version>0.0.14</version>
</dependency>

<!-- Maven (Spring Boot 3.2.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor_3</artifactId>
  <version>0.0.14</version>
</dependency>
```

To view the latest versions, refer to the following:
- [spring-boot-starter-actor](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor)
- [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

### Basic Configuration

Enable actor support in your `application.yml`:

```yaml
spring:
  actor-enabled: true
  actor:
    pekko:
      actor:
        provider: local
```

### Try the Demo

Run the chat example with multiple nodes:

```shell
# Start 3 chat application instances on ports 8080, 8081, and 8082
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Stop the cluster
$ sh cluster-stop.sh
```

## Metrics and Monitoring

The library includes a metrics module for monitoring actor performance and a ready-to-use monitoring stack based on Prometheus and Grafana.

### Running the Monitoring Stack

1. Start the chat application cluster:
```shell
$ sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3
```

2. Start the monitoring stack:
```shell
$ cd scripts/monitoring && docker-compose up -d
```

3. Access the monitoring dashboards:
   - Prometheus: http://localhost:9090
   - Grafana: http://localhost:3000 (username: admin, password: admin)

4. Shutdown the monitoring stack when done:
```shell
$ cd scripts/monitoring && docker-compose down -v
```

The monitoring setup provides dashboards for visualizing actor metrics, including message processing times and message counts by type.
