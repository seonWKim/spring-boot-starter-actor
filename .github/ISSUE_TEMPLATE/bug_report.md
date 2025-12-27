---
name: Bug Report
about: Report a bug to help us improve
title: '[BUG] '
labels: 'bug'
assignees: ''
---

## Bug Description
A clear and concise description of what the bug is.

## Environment
- **Library Version**: (e.g., 0.8.0)
- **Spring Boot Version**: (e.g., 2.7.18 or 3.2.5)
- **Java Version**: (e.g., Java 17)
- **Build Tool**: (e.g., Gradle 8.x, Maven 3.x)
- **Operating System**: (e.g., Ubuntu 22.04, macOS 14, Windows 11)
- **Deployment**: (e.g., local, Kubernetes, Docker)

## Steps to Reproduce
Steps to reproduce the behavior:
1. Configure actor with '...'
2. Send message '...'
3. Observe error '...'

## Expected Behavior
A clear and concise description of what you expected to happen.

## Actual Behavior
A clear and concise description of what actually happened.

## Code Sample
```java
// Minimal reproducible example
@Component
public class MyActor implements SpringActor<MyActor.Command> {
    // Your code here
}
```

## Stack Trace
```
Paste any relevant stack traces or error messages here
```

## Configuration
```yaml
# Relevant application.yml configuration
spring:
  actor:
    pekko:
      # Your config
```

## Logs
```
Paste relevant log output here
```

## Additional Context
Add any other context about the problem here (screenshots, related issues, etc.).

## Possible Solution
If you have suggestions on how to fix the bug, please describe them here.

## Checklist
- [ ] I have searched for similar issues
- [ ] I have tested with the latest version
- [ ] I have included all necessary information
- [ ] I have provided a minimal reproducible example
