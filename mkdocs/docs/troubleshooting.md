# Troubleshooting Guide

This guide helps you diagnose and resolve common issues with spring-boot-starter-actor.

## Table of Contents

- [Actor System Issues](#actor-system-issues)
- [Messaging Problems](#messaging-problems)
- [Cluster Issues](#cluster-issues)
- [Serialization Errors](#serialization-errors)
- [Performance Problems](#performance-problems)
- [Configuration Issues](#configuration-issues)
- [Getting Help](#getting-help)

---

## Actor System Issues

### Actor System Fails to Start

**Symptoms**: Application fails to start with actor system initialization errors.

**Common Causes**:

1. **Missing `@EnableActorSupport` annotation**
   ```java
   @SpringBootApplication
   @EnableActorSupport  // <-- Don't forget this!
   public class Application {
       public static void main(String[] args) {
           SpringApplication.run(Application.class, args);
       }
   }
   ```

2. **Conflicting Pekko versions**
   - Ensure you're using compatible Pekko versions
   - Check `gradle.properties` for version requirements

3. **Invalid configuration**
   - Verify `application.yml` syntax
   - Check for typos in configuration keys

**Solution**:
```bash
# Check for configuration errors
./gradlew build --debug

# Verify Pekko dependencies
./gradlew dependencies | grep pekko
```

### Actor Not Found

**Symptoms**: `ActorNotFoundException` or actor handle returns null.

**Common Causes**:

1. **Actor not spawned before use**
   ```java
   // ❌ Wrong - actor doesn't exist yet
   CompletionStage<SpringActorHandle<Command>> handle = 
       actorSystem.get(MyActor.class, "my-actor");
   
   // ✅ Correct - spawn first
   CompletionStage<SpringActorHandle<Command>> handle = 
       actorSystem.getOrSpawn(MyActor.class, "my-actor");
   ```

2. **Incorrect actor ID**
   - Actor IDs are case-sensitive
   - Ensure consistent ID usage

3. **Actor was stopped**
   - Check if actor was terminated
   - Use lifecycle monitoring

**Solution**:
```java
// Check if actor exists before using
CompletionStage<Boolean> exists = 
    actorSystem.exists(MyActor.class, "my-actor");

exists.thenAccept(doesExist -> {
    if (doesExist) {
        // Use actor
    } else {
        // Spawn actor first
    }
});
```

---

## Messaging Problems

### Messages Not Received

**Symptoms**: Actor doesn't respond to messages.

**Common Causes**:

1. **No message handler registered**
   ```java
   @Override
   public SpringActorBehavior<Command> create(SpringActorContext ctx) {
       return SpringActorBehavior.builder(Command.class, ctx)
           .onMessage(MyMessage.class, (context, msg) -> {  // <-- Handler needed
               // Process message
               return Behaviors.same();
           })
           .build();
   }
   ```

2. **Wrong message type**
   - Ensure message type matches handler
   - Check for typos in class names

3. **Actor is stopped or failed**
   - Check actor lifecycle state
   - Review supervision strategy

**Debugging**:
```java
// Enable debug logging
logging:
  level:
    org.apache.pekko: DEBUG
    io.github.seonwkim: DEBUG
```

### Ask Timeout

**Symptoms**: `AskTimeoutException` when using ask pattern.

**Common Causes**:

1. **Timeout too short**
   ```java
   // ❌ Too short for complex operations
   actor.ask(new GetData())
       .withTimeout(Duration.ofMillis(100))
       .execute();
   
   // ✅ Reasonable timeout
   actor.ask(new GetData())
       .withTimeout(Duration.ofSeconds(5))
       .execute();
   ```

2. **Actor not replying**
   ```java
   // ❌ Forgot to reply
   .onMessage(GetValue.class, (ctx, msg) -> {
       String value = calculateValue();
       // Missing: msg.reply(value);
       return Behaviors.same();
   })
   
   // ✅ Always reply to AskCommand
   .onMessage(GetValue.class, (ctx, msg) -> {
       String value = calculateValue();
       msg.reply(value);  // <-- Don't forget!
       return Behaviors.same();
   })
   ```

3. **Blocking operation in actor**
   - Actors should not block
   - Use CompletionStage for async operations

**Solution**:
```java
// Add timeout handler
actor.ask(new GetData())
    .withTimeout(Duration.ofSeconds(5))
    .onTimeout(() -> "default-value")  // <-- Fallback
    .execute();
```

### Dead Letters

**Symptoms**: Messages sent to `DeadLetter` (actor terminated or non-existent).

**Common Causes**:

1. **Sending to stopped actor**
2. **Wrong actor path**
3. **Actor crashed and not restarted**

**Monitoring**:
```java
// Monitor dead letters
spring:
  actor:
    pekko:
      actor:
        debug:
          unhandled: on
          lifecycle: on
```

---

## Cluster Issues

### Cluster Doesn't Form

**Symptoms**: Nodes don't join cluster, isolated nodes.

**Common Causes**:

1. **Incorrect seed nodes**
   ```yaml
   # ❌ Wrong - localhost only works locally
   spring:
     actor:
       pekko:
         cluster:
           seed-nodes:
             - "pekko://ActorSystem@localhost:2551"
   
   # ✅ Correct - use actual hostnames
   spring:
     actor:
       pekko:
         cluster:
           seed-nodes:
             - "pekko://ActorSystem@node1.example.com:2551"
             - "pekko://ActorSystem@node2.example.com:2551"
   ```

2. **Network issues**
   - Verify ports are open (default: 2551)
   - Check firewall rules
   - Ensure nodes can reach each other

3. **Different actor system names**
   - All nodes must use same system name

**Debugging**:
```bash
# Test network connectivity
telnet node1.example.com 2551

# Check cluster status
curl http://localhost:8080/actuator/health
```

### Split Brain

**Symptoms**: Cluster partitions into multiple sub-clusters.

**Causes**:
- Network partition
- Node failures
- No split-brain resolver configured

**Solution**:
```yaml
spring:
  actor:
    pekko:
      cluster:
        downing-provider-class: "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
        split-brain-resolver:
          active-strategy: keep-majority
```

See [Split Brain Resolver](../guides/split-brain-resolver.md) guide.

---

## Serialization Errors

### Serialization Exception

**Symptoms**: `NotSerializableException` when sending messages in cluster.

**Common Causes**:

1. **Message doesn't implement JsonSerializable**
   ```java
   // ❌ Not serializable
   public record MyMessage(String data) implements Command {}
   
   // ✅ Serializable
   public record MyMessage(String data) 
       implements Command, JsonSerializable {}
   ```

2. **Jackson version mismatch**
   - Pekko requires Jackson 2.17.3+
   - Check dependency management

**Solution**:
```gradle
dependencyManagement {
    imports {
        mavenBom("com.fasterxml.jackson:jackson-bom:2.17.3")
    }
}
```

### Deserialization Fails

**Common Causes**:
- Class not found on receiving node
- Different class versions
- Missing no-arg constructor

**Solution**:
```java
// Ensure all message classes are available on all nodes
// Use DTOs for cross-version compatibility
public record MessageDTO(String field1, int field2) 
    implements JsonSerializable {}
```

---

## Performance Problems

### High Message Latency

**Symptoms**: Slow message processing, timeouts.

**Common Causes**:

1. **Blocking operations in actors**
   ```java
   // ❌ Blocking - BAD!
   .onMessage(FetchData.class, (ctx, msg) -> {
       String data = blockingHttpClient.get("/api/data");  // Blocks!
       return Behaviors.same();
   })
   
   // ✅ Non-blocking - GOOD!
   .onMessage(FetchData.class, (ctx, msg) -> {
       CompletionStage<String> future = asyncHttpClient.get("/api/data");
       future.thenAccept(data -> {
           ctx.getSelf().tell(new DataReceived(data));
       });
       return Behaviors.same();
   })
   ```

2. **Wrong dispatcher configuration**
   - Use appropriate dispatcher for workload
   - Configure thread pool sizes

3. **Mailbox overflow**
   - Messages piling up
   - Actor can't keep up with load

**Solution**:
```yaml
spring:
  actor:
    pekko:
      actor:
        default-dispatcher:
          fork-join-executor:
            parallelism-min: 8
            parallelism-max: 64
```

### High Memory Usage

**Common Causes**:
- Too many actors
- Large messages
- Memory leaks in actor state

**Monitoring**:
```java
// Enable metrics
implementation 'io.github.seonwkim:spring-boot-starter-actor-metrics:0.8.0'

// Check actor count
curl http://localhost:8080/actuator/metrics/system.active-actors
```

---

## Configuration Issues

### Configuration Not Applied

**Symptoms**: Configuration changes don't take effect.

**Common Causes**:

1. **Wrong configuration location**
   - Must be under `spring.actor.pekko.*`

2. **Invalid YAML syntax**
   - Check indentation
   - Use YAML validator

3. **Configuration overridden**
   - Check for multiple config sources
   - Review Spring profiles

**Verification**:
```bash
# View effective configuration
./gradlew bootRun --debug | grep "spring.actor"
```

### Spring Boot Version Compatibility

**Problem**: Wrong artifact for Spring Boot version.

**Solution**:
```gradle
// Spring Boot 2.7.x
implementation 'io.github.seonwkim:spring-boot-starter-actor:0.8.0'

// Spring Boot 3.2.x
implementation 'io.github.seonwkim:spring-boot-starter-actor_3:0.8.0'
```

---

## Common Error Messages

### "ActorSystem failed to start"

**Cause**: Configuration error or dependency conflict.

**Solution**:
1. Check `@EnableActorSupport` annotation
2. Verify Pekko dependencies
3. Review application logs for details

### "Unable to serialize message"

**Cause**: Message class not serializable.

**Solution**:
```java
// Implement JsonSerializable
public record MyMessage(String data) 
    implements Command, JsonSerializable {}
```

### "No such actor"

**Cause**: Actor not spawned or already stopped.

**Solution**:
```java
// Use getOrSpawn instead of get
actorSystem.getOrSpawn(MyActor.class, "my-actor");
```

---

## Getting Help

If you're still stuck:

1. **Check documentation**: [seonwkim.github.io/spring-boot-starter-actor](https://seonwkim.github.io/spring-boot-starter-actor/)
2. **Search issues**: [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues)
3. **Ask community**: 
   - [Discord](https://discord.com/channels/1439734161614045205/1439734162100846655)
   - [GitHub Discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions)
4. **Report bug**: Create a [bug report](https://github.com/seonwkim/spring-boot-starter-actor/issues/new?template=bug_report.md)

### Information to Include

When asking for help, provide:

- Library version
- Spring Boot version
- Java version
- Relevant configuration
- Stack traces
- Minimal reproducible example

---

## Debugging Tips

### Enable Debug Logging

```yaml
logging:
  level:
    org.apache.pekko: DEBUG
    io.github.seonwkim: DEBUG
```

### Use Pekko TestKit

```java
@Test
void testActorBehavior() {
    ActorTestKit testKit = ActorTestKit.create();
    TestProbe<Response> probe = testKit.createTestProbe();
    
    ActorRef<Command> actor = testKit.spawn(MyActor.create());
    actor.tell(new MyCommand(probe.ref()));
    
    probe.expectMessage(new Response("expected"));
}
```

### Monitor with Metrics

```bash
# Check actor metrics
curl http://localhost:8080/actuator/metrics/actor.processing-time

# Check system health
curl http://localhost:8080/actuator/health
```

---

**Last Updated**: December 2025
