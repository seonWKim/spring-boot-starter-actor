# Getting Started with Spring Boot Starter Actor

This guide will help you get started with Spring Boot Starter Actor in your Spring Boot application.

## Prerequisites

- Java 11 or higher
- Spring Boot 2.x or 3.x

## Installation

Add the dependency to your project:

```gradle
// Manually overwrite spring managed jackson dependency 
dependencyManagement {
    imports {
        // require minimum 2.17.3 version of jackson
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}

// Gradle(spring boot 2.7.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.3.0'

// Gradle(spring boot 3.2.x)
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.3.0'
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

<!-- Maven(spring boot 2.7.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor</artifactId>
  <version>0.3.0</version>
</dependency>

<!-- Maven(spring boot 3.2.x) -->
<dependency>
  <groupId>io.github.seonwkim</groupId>
  <artifactId>spring-boot-starter-actor_3</artifactId>
  <version>0.3.0</version>
</dependency>
```

To view the latest versions, refer to the following:
- [spring-boot-starter-actor](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor)
- [spring-boot-starter-actor_3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

## Basic Configuration

Spring Boot Starter Actor uses Spring Boot's auto-configuration to set up the actor system. By default, it will create a local actor system with sensible defaults.

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
2. [Create sharded actors for clustered environments](guides/sharded-actors.md)
3. Explore the [API Reference](api-reference.md) for detailed information about the library's APIs
