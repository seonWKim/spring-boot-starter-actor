# Spring Boot Starter Actor

## Project Purpose

Spring Boot Starter Actor is a library that integrates Spring Boot with the actor model
using [Apache Pekko](https://pekko.apache.org/) (an open-source, community-driven fork of Akka). The project
bridges the gap between Spring Boot and the actor model, allowing developers to build stateful applications
using familiar Spring Boot patterns while leveraging the power of the actor model for managing state and
concurrency.

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
- Support for both Spring Boot 2.x and 3.x

## Code Style and Architecture

### Code Style

The project follows these code style conventions:

1. **Comprehensive JavaDoc Comments**: All classes, interfaces, and public methods have detailed JavaDoc
   comments explaining their purpose, usage, and relationships with other components.

2. **Clean Code Principles**: The code follows clean code principles with meaningful variable names, single
   responsibility principle, and clear separation of concerns.

3. **Type Safety**: Extensive use of generics to ensure type safety throughout the codebase.

4. **Consistent Formatting**: The project uses Spotless for code formatting to maintain consistency.

5. **Immutability**: Preference for immutable objects and final fields where appropriate.

### Architecture

The core architecture consists of several key components:

1. **SpringActor Interface**: The main interface for actor implementations that can be managed by the Spring
   actor system.

2. **SpringActorContext**: Represents the context for an actor in the Spring Actor system, providing a way to
   identify actors uniquely.

3. **SpringActorRef**: A wrapper around Pekko's ActorRef that provides methods for asking and telling messages
   to an actor with a more Spring-friendly API.

4. **SpringShardedActorRef**: Similar to SpringActorRef but for sharded actors in a cluster environment.

5. **SpringActorSystemBuilder**: Used to build and configure the actor system.

The library is organized into several modules:

- **core**: Core functionality for Spring Boot 2.x
- **core-boot3**: Core functionality for Spring Boot 3.x
- **metrics**: Support for monitoring actor performance
- **example**: Example applications demonstrating usage patterns

## Usage Examples

The project includes several example applications:

1. **Simple Example**: Demonstrates basic actor creation and message passing.

2. **Chat Example**: A more complex application showing how to build a real-time chat system using actors.

3. **Synchronization Example**: Demonstrates different synchronization methods for incrementing a counter value.

4. **Cluster Example**: Shows how to set up and use a clustered actor system.

## Dependencies

The project primarily depends on:

- Apache Pekko libraries (actor-typed, cluster-typed, cluster-sharding-typed, serialization-jackson)
- Spring Boot (2.x or 3.x)

## Testing and Monitoring

The library includes:

- Test utilities for testing actors
- A metrics module for monitoring actor performance
- A ready-to-use monitoring stack based on Prometheus and Grafana

## Common Task Commands

### Build Commands
```bash
# Clean and build all modules
./gradlew clean build

# Build without tests
./gradlew build -x test

# Build specific module
./gradlew :core:build
./gradlew :core-boot3:build
./gradlew :metrics:build

# Note: Spotless is currently commented out in build.gradle.kts
# To use spotless, uncomment the configuration in build.gradle.kts
```

### Test Commands
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :core:test
./gradlew :core-boot3:test

# Run custom test task (runs core and core-boot3 tests)
./gradlew runTest
```

### Running Examples
```bash
# Run simple example
./gradlew :example:simple:bootRun

# Run chat example
./gradlew :example:chat:bootRun

# Run synchronization example
./gradlew :example:synchronization:bootRun

# Run cluster example (requires multiple instances)
./gradlew :example:cluster:bootRun
```

### Publishing
```bash
# Publish to local Maven repository
./gradlew publishToMavenLocal

# Publish to Maven Central (requires credentials)
./gradlew publish
```

## File Path Conventions

### Core Module Structure
```
spring-boot-starter-actor/
├── core/                                    # Spring Boot 2.x support
│   └── src/main/java/io/github/seonwkim/core/
│       ├── SpringActor.java               # Main actor interface
│       ├── SpringActorContext.java        # Actor context
│       ├── SpringActorRef.java            # Actor reference wrapper
│       └── SpringActorSystemBuilder.java  # System builder
├── core-boot3/                             # Spring Boot 3.x support
│   └── src/main/java/io/github/seonwkim/core/  # Same structure as core
├── metrics/                                # Metrics and monitoring
│   └── src/main/java/io/github/seonwkim/
│       └── metrics/                       # Metrics implementations
└── example/                                # Example applications
    ├── simple/                            # Basic usage example
    ├── chat/                              # Chat application
    ├── synchronization/                   # Synchronization patterns
    └── cluster/                           # Cluster setup example
```

### Test Structure
```
{module}/src/test/java/                    # Unit tests
```

### Configuration Files
```
src/main/resources/
├── application.yml                        # Spring Boot configuration
```

## Quick Package Reference

### Core Packages
- `io.github.seonwkim.core` - Root package for all core components
- `io.github.seonwkim.core.behavior` - Actor behavior implementations
- `io.github.seonwkim.core.impl` - Default implementations
- `io.github.seonwkim.core.serialization` - Serialization interfaces
- `io.github.seonwkim.core.shard` - Sharding support
- `io.github.seonwkim.core.utils` - Utility classes

### Metrics Package
- `io.github.seonwkim.metrics` - Metrics collection and reporting

### Example Packages
- `io.github.seonwkim.example` - Example applications
- `io.github.seonwkim.example.counter` - Counter synchronization examples

## Development Workflow

### 1. Setting Up Development Environment
```bash
# Clone the repository
git clone https://github.com/seonwkim/spring-boot-starter-actor.git
cd spring-boot-starter-actor

# Build the project
./gradlew clean build

# Import into IDE (IntelliJ IDEA recommended)
# File -> Open -> Select build.gradle.kts
```

### 2. Making Changes
1. Create a feature branch: `git checkout -b feature/your-feature`
2. Make your changes following the code style conventions
3. Write/update tests for your changes
4. Run tests: `./gradlew test` or `./gradlew runTest`
5. Commit with descriptive message

### 3. Testing Changes
```bash
# Run unit tests
./gradlew test

# Test in example applications
./gradlew :example:simple:bootRun
```

### 4. Pre-commit Checklist
- [ ] Code follows project style conventions
- [ ] All tests pass
- [ ] JavaDoc comments added/updated
- [ ] Code follows consistent formatting
- [ ] Examples still work if core changes made

### 5. Debugging
```bash
# Run with debug output
./gradlew test --debug

# Run specific test class
./gradlew test --tests "YourTestClass"

# Run with increased heap size
./gradlew test -Dorg.gradle.jvmargs="-Xmx2g"
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Build Failures
**Issue**: `Could not resolve dependencies`
```bash
# Clear Gradle cache and retry
./gradlew clean build --refresh-dependencies
```

#### 2. Test Failures
**Issue**: `ActorSystem already exists`
```bash
# Ensure proper cleanup in tests
# Use @DirtiesContext annotation in Spring tests
# Check for proper ActorSystem termination
```

#### 3. Code Formatting
**Issue**: `Code formatting inconsistencies`
```bash
# Note: Spotless is currently commented out in build.gradle.kts
# To enable spotless, uncomment the configuration in build.gradle.kts
# Then run: ./gradlew spotlessApply
```

#### 4. Cluster Example Not Working
**Issue**: `Unable to join cluster`
```bash
# Check port availability (default: 2551-2553)
lsof -i :2551

# Ensure proper cluster configuration in application-cluster.yml
# Start seed nodes first
```

#### 5. Memory Issues
**Issue**: `OutOfMemoryError during tests`
```bash
# Increase JVM heap size
export GRADLE_OPTS="-Xmx2g -XX:MaxPermSize=512m"
./gradlew test
```

#### 6. IDE Import Issues
**Issue**: `Cannot resolve symbol` in IDE
- Refresh Gradle project in IDE
- Invalidate caches and restart (IntelliJ: File -> Invalidate Caches)
- Ensure Java 11+ is configured

#### 7. Pekko Configuration Issues
**Issue**: `Missing configuration`
- Check `reference.conf` in resources
- Verify configuration hierarchy
- Use `ConfigFactory.load()` for debugging

### Debug Logging
Enable debug logging for troubleshooting:
```yaml
# application.yml
logging:
  level:
    io.github.seonwkim: DEBUG
    org.apache.pekko: DEBUG
```

## Additional Information

- The project supports both local development and Docker-based deployment
- Cluster management scripts are provided for starting and stopping clusters
- The library is published to Maven Central
- Licensed under Apache License 2.0
- Supports Java 11 or higher
