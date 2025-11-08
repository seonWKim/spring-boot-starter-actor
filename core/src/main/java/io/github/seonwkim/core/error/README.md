# Enhanced Error Message Framework

This framework provides developer-friendly error messages with troubleshooting hints and documentation links for Spring Actor System errors.

## Overview

The error message framework automatically detects common error scenarios and enhances them with:
- Clear, actionable troubleshooting hints
- Documentation links for detailed information
- Actor context information (path, message type, state)
- Pretty-formatted error output in verbose mode
- Compact error format for production

## Features

### Error Type Detection
The framework automatically detects 15 types of errors:
- **Bean Not Found**: Missing Spring beans or dependency injection errors
- **Actor Spawn Failure**: Actor creation failures
- **Timeout**: Ask timeouts
- **Dead Letter**: Messages sent to stopped actors
- **Unhandled Message**: Message types without handlers
- **Serialization Error**: Message serialization failures in cluster mode
- **Cluster Formation**: Cluster startup failures
- **Cluster Unreachable**: Unreachable cluster nodes
- **Cluster Split Brain**: Split brain scenarios
- **Sharding Not Started**: Sharding region not initialized
- **Configuration Error**: Invalid configuration
- **Invalid Actor Reference**: Invalid actor paths
- **Restart Loop**: Repeated actor failures
- **Supervision Error**: Supervision strategy issues
- **Unknown**: Generic errors

### Two Display Modes

#### Compact Mode (Production)
Single-line format with first hint:
```
BeanCreationException: orderRepository not found
💡 Hint: Ensure OrderActor is annotated with @Component, @Repository, or @Service
📖 Docs: https://docs.spring-actor.io/troubleshooting#bean-not-found
🎯 Actor: /user/order-actor
```

#### Verbose Mode (Development)
Boxed format with all hints and context:
```
╭─ BeanCreationException ──────────────────────────────────────────╮
│ orderRepository not found                                         │
│                                                                   │
│ 💡 Troubleshooting hints:                                         │
│   1. Ensure OrderActor is annotated with @Component, @Repository│
│   2. Check if component scanning includes the package            │
│   3. Verify that the Spring context is properly configured      │
│   4. Check if required dependencies are in the classpath        │
│                                                                   │
│ 📖 Documentation:                                                 │
│   https://docs.spring-actor.io/troubleshooting#bean-not-found    │
│                                                                   │
│ 🎯 Actor Context:                                                 │
│   - Actor Path: /user/order-actor                               │
│   - Actor Class: OrderActor                                     │
│                                                                   │
│ 🔍 Stack trace: [See below]                                      │
╰───────────────────────────────────────────────────────────────────╯
```

## Usage

### Basic Usage

```java
import io.github.seonwkim.core.error.ActorErrorMessage;

try {
    // Some actor operation
} catch (Exception e) {
    // Format error with hints
    String errorMessage = ActorErrorMessage.formatCompact(e);
    logger.error(errorMessage);
}
```

### With Context Information

```java
import io.github.seonwkim.core.error.ActorErrorMessage;
import io.github.seonwkim.core.error.ErrorContext;

try {
    // Actor operation
} catch (Exception e) {
    ErrorContext context = ErrorContext.builder()
        .actorClass("OrderActor")
        .actorPath("/user/order-actor")
        .messageType("CreateOrder")
        .actorState("PROCESSING")
        .build();
    
    String errorMessage = ActorErrorMessage.format(e, context);
    logger.error(errorMessage);
}
```

### Verbose Mode for Development

```java
import io.github.seonwkim.core.error.ActorErrorMessage;
import io.github.seonwkim.core.error.ErrorContext;

try {
    // Actor operation
} catch (Exception e) {
    ErrorContext context = ErrorContext.builder()
        .actorPath("/user/payment-actor")
        .messageType("ProcessPayment")
        .additionalInfo("currentTimeout", "3s")
        .build();
    
    String errorMessage = ActorErrorMessage.formatVerbose(e, context);
    logger.error(errorMessage);
}
```

### Adding Custom Context Information

```java
ErrorContext context = ErrorContext.builder()
    .actorPath("/user/my-actor")
    .messageType("MyMessage")
    .additionalInfo("timeout", "5s")
    .additionalInfo("mailboxSize", "100")
    .additionalInfo("customField", "customValue")
    .build();
```

### Error Type Detection

```java
import io.github.seonwkim.core.error.ActorErrorMessage;
import io.github.seonwkim.core.error.ErrorType;

Exception error = // some exception
ErrorType type = ActorErrorMessage.detectErrorType(error);

if (type == ErrorType.TIMEOUT) {
    // Handle timeout specifically
} else if (type == ErrorType.BEAN_NOT_FOUND) {
    // Handle bean not found
}
```

## Troubleshooting Hints Database

Hints are stored in `core/src/main/resources/error-hints.yml`. Each error type has:
- Multiple troubleshooting hints
- Documentation link
- Variable substitution support

Example from `error-hints.yml`:
```yaml
error-types:
  timeout:
    hints:
      - "Increase timeout duration (current: {currentTimeout})"
      - "Check if actor {actorPath} is processing messages"
      - "Verify the actor hasn't stopped or terminated"
      - "Check mailbox size - the actor may be backed up with messages"
    doc-link: "/troubleshooting#timeout"
```

Variables like `{currentTimeout}` and `{actorPath}` are automatically substituted from the ErrorContext.

## Integration with Existing Code

To enhance existing exception handling:

```java
// Before
throw new RuntimeException("Failed to spawn actor");

// After
throw new RuntimeException(
    ActorErrorMessage.format(
        new RuntimeException("Failed to spawn actor"),
        ErrorContext.builder()
            .actorClass("MyActor")
            .build()
    )
);
```

Or catch and re-throw with enhanced message:

```java
try {
    actorSystem.spawn(actorClass);
} catch (Exception e) {
    ErrorContext context = ErrorContext.builder()
        .actorClass(actorClass.getSimpleName())
        .build();
    throw new RuntimeException(ActorErrorMessage.format(e, context), e);
}
```

## Examples

See `ErrorMessageIntegrationTest.java` for comprehensive examples of different error scenarios.

## Testing

Run the error framework tests:
```bash
./gradlew :core:test --tests "io.github.seonwkim.core.error.*"
```

## Architecture

### Components

1. **ErrorType**: Enum defining all error categories
2. **ErrorContext**: Contextual information about the error (actor path, message type, etc.)
3. **TroubleshootingHints**: Loads and provides hints from YAML database
4. **ErrorMessageBuilder**: Formats error messages in compact or verbose mode
5. **ActorErrorMessage**: Main API for error formatting and detection

### Design Principles

- **Automatic Detection**: Error types are detected automatically from exception types and messages
- **Contextual**: Hints adapt based on the error context provided
- **Actionable**: Every hint provides specific steps developers can take
- **Non-intrusive**: Can be added to existing code without major changes
- **Testable**: Comprehensive test coverage ensures reliability

## Future Enhancements

Potential future improvements:
- Configuration properties for error message formatting preferences
- Internationalization (i18n) support for hints
- Custom hint providers for domain-specific errors
- Integration with Spring Boot's error handling
- Metrics collection for common error patterns
