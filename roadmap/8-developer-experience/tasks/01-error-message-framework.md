# Task 1.1: Enhanced Error Message Framework

**Priority:** HIGH
**Estimated Effort:** 1 week
**Dependencies:** None
**Assignee:** AI Agent

---

## Objective

Transform generic error messages into actionable, developer-friendly messages with troubleshooting hints and documentation links.

---

## Problem Statement

**Current State:**
```
ActorSpawnException: Failed to spawn actor 'order-actor'
Cause: BeanCreationException: Error creating bean with name 'orderRepository'
```

**Desired State:**
```
ActorSpawnException: Failed to spawn actor 'order-actor'
Cause: OrderRepository bean not found

💡 Troubleshooting hints:
  1. Ensure OrderRepository is annotated with @Repository
  2. Check if component scanning includes the repository package
  3. Verify database configuration in application.yml
  4. Check if required dependencies are in classpath

📖 Documentation: https://docs.spring-actor.io/troubleshooting#bean-not-found
🔍 Stack trace: [Show full trace]
```

---

## Common Error Scenarios to Handle

### 1. Bean Not Found Errors
**Trigger:** Actor dependencies not in Spring context
**Hints:**
- Check @Component/@Repository/@Service annotations
- Verify component scanning configuration
- Check dependency injection in constructor

### 2. Timeout Errors
**Trigger:** Actor ask() times out
**Hints:**
- Increase timeout duration
- Check if actor is processing messages
- Verify actor hasn't stopped
- Check mailbox size (may be backed up)

### 3. Serialization Errors (Cluster Mode)
**Trigger:** Message not serializable
**Hints:**
- Implement JsonSerializable interface
- Add Jackson annotations
- Check message contains only serializable fields
- Verify no lambda expressions in messages

### 4. Cluster Formation Errors
**Trigger:** Unable to join cluster
**Hints:**
- Check seed node configuration
- Verify network connectivity
- Check if ports are open (default 2551)
- Verify hostname resolution

### 5. Configuration Errors
**Trigger:** Invalid application.yml
**Hints:**
- Validate YAML syntax
- Check required properties present
- Verify property types match expected
- Review configuration reference docs

### 6. Actor Already Exists
**Trigger:** Trying to spawn actor with duplicate ID
**Hints:**
- Use unique actor IDs
- Use getOrSpawn() instead of spawn()
- Check if actor was already created

---

## Implementation Requirements

### 1. Error Message Builder

```java
public class ActorErrorMessage {

    public static String format(Throwable error, ErrorContext context) {
        ErrorMessageBuilder builder = new ErrorMessageBuilder(error);

        // Detect error type
        ErrorType type = detectErrorType(error);

        // Add troubleshooting hints
        builder.addHints(getHintsFor(type, context));

        // Add documentation link
        builder.addDocLink(getDocLinkFor(type));

        // Format stack trace
        builder.addStackTrace(error);

        return builder.build();
    }

    private static ErrorType detectErrorType(Throwable error) {
        if (error instanceof BeanCreationException) {
            return ErrorType.BEAN_NOT_FOUND;
        } else if (error instanceof TimeoutException) {
            return ErrorType.TIMEOUT;
        }
        // ... more error types
    }
}
```

### 2. Error Context

```java
public class ErrorContext {
    private String actorClass;
    private String actorId;
    private String messageType;
    private Map<String, Object> additionalInfo;

    // Getters and builders
}
```

### 3. Troubleshooting Hints Database

```yaml
# resources/error-hints.yml
error-types:
  bean-not-found:
    hints:
      - "Ensure {beanClass} is annotated with @Component, @Repository, or @Service"
      - "Check if component scanning includes package {beanPackage}"
      - "Verify required dependencies are in classpath"
    doc-link: "/troubleshooting#bean-not-found"

  timeout:
    hints:
      - "Increase timeout duration (current: {currentTimeout})"
      - "Check if actor {actorPath} is processing messages"
      - "Verify mailbox size (current: {mailboxSize})"
    doc-link: "/troubleshooting#timeout"
```

### 4. Integration Points

**Modify these exception classes:**
1. `ActorSpawnException`
2. `ActorAskTimeoutException`
3. `MessageSerializationException`
4. `ClusterFormationException`
5. `ActorConfigurationException`

Each should use `ActorErrorMessage.format()` for their message.

---

## Files to Create

1. **`core/src/main/java/io/github/seonwkim/core/error/ActorErrorMessage.java`**
   - Main error formatting class

2. **`core/src/main/java/io/github/seonwkim/core/error/ErrorType.java`**
   - Enum of error types

3. **`core/src/main/java/io/github/seonwkim/core/error/ErrorContext.java`**
   - Context information for errors

4. **`core/src/main/java/io/github/seonwkim/core/error/TroubleshootingHints.java`**
   - Hint generation logic

5. **`core/src/main/resources/error-hints.yml`**
   - Database of error scenarios and hints

---

## Configuration

```yaml
spring:
  actor:
    error-messages:
      enabled: true
      include-stack-trace: auto  # auto, always, never
      include-hints: true
      include-doc-links: true
      doc-base-url: "https://docs.spring-actor.io"
```

---

## Testing

```java
@Test
public void testBeanNotFoundError() {
    BeanCreationException cause = new BeanCreationException("orderRepository");
    ErrorContext context = ErrorContext.builder()
        .actorClass("OrderActor")
        .build();

    String formatted = ActorErrorMessage.format(cause, context);

    assertThat(formatted).contains("OrderRepository bean not found");
    assertThat(formatted).contains("💡 Troubleshooting hints:");
    assertThat(formatted).contains("@Repository");
    assertThat(formatted).contains("📖 Documentation:");
}
```

---

## Acceptance Criteria

- [ ] All common error scenarios handled
- [ ] Troubleshooting hints are actionable
- [ ] Documentation links included
- [ ] Stack traces properly formatted
- [ ] Context information included when available
- [ ] Configuration to control error message detail
- [ ] Tests for all error types
- [ ] Documentation updated with error troubleshooting guide

---

## Example Output Formats

### Compact Mode (Production)
```
ActorSpawnException: Failed to spawn 'order-actor' - OrderRepository bean not found
Hint: Check @Repository annotation and component scanning
Docs: https://docs.spring-actor.io/troubleshooting#bean-not-found
```

### Verbose Mode (Development)
```
╭─ ActorSpawnException ────────────────────────────────────────────╮
│ Failed to spawn actor 'order-actor'                              │
│ Cause: OrderRepository bean not found                            │
│                                                                   │
│ 💡 Troubleshooting hints:                                         │
│   1. Ensure OrderRepository is annotated with @Repository        │
│   2. Check if component scanning includes the repository package │
│   3. Verify database configuration in application.yml            │
│   4. Check if required dependencies are in classpath             │
│                                                                   │
│ 📖 Documentation:                                                 │
│   https://docs.spring-actor.io/troubleshooting#bean-not-found    │
│                                                                   │
│ 🔍 Stack trace: [Show full trace]                                │
╰───────────────────────────────────────────────────────────────────╯
```

---

## Notes

- Error messages should be helpful without being patronizing
- Hints should be specific to the error context when possible
- Documentation links should deep-link to relevant sections
- Consider internationalization in future (i18n support)
