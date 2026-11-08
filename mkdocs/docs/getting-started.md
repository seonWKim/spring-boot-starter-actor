# Getting Started

This guide will help you get started with Spring Boot Starter Actor in your Spring Boot application.

## Prerequisites

- Java 21 or higher
- Spring Boot 3.5 or higher

Spring Boot 2 users stay on the 0.x line (`spring-boot-starter-actor`). See [Migrating from 0.x](migrating-from-0x.md).

## Installation

Add the dependency to your project:

### Gradle
```gradle
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.10.0'
```

### Maven
```xml
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor_3</artifactId>
  <version>0.10.0</version>
</dependency>
```

To view the latest version, see [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3).

!!! note
    Spring Boot 3.5 manages Jackson 2.18+, which satisfies Pekko. No `jackson-bom` override is needed.

## Basic Configuration

Spring Boot Starter Actor uses Spring Boot's auto-configuration to set up the actor system. By default, it creates a local actor system with sensible defaults.

### Enable Actor Support

Add the `@EnableActorSupport` annotation to your Spring Boot application class:

```java
@SpringBootApplication
@EnableActorSupport
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### Application Properties

You can customize the actor system using application properties (or yaml):

```yaml
# application.properties or application.yml
spring:
  application:
    name: spring-pekko
  actor:
    pekko:
      actor:
        provider: local
```

## Next Steps

Now that you have set up Spring Boot Starter Actor in your project, you can:

1. [Learn how to register actors and send messages](guides/actor-registration-messaging.md)
2. [Explore pub/sub topics for distributed messaging](guides/pub-sub-topics.md)
3. [Create sharded actors for clustered environments](guides/sharded-actors.md)
4. [Add metrics and monitoring to your actor system](examples/monitoring.md)
