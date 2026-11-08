# Changelog

## 1.0.0 (unreleased)

### Breaking changes

- **Java 21+ and Spring Boot 3.5+ are required.** The Spring Boot 2 build (`spring-boot-starter-actor`,
  Java 11) is no longer published from this line. Boot 2 users stay on 0.x.
- The `jackson-bom` override is no longer needed: Spring Boot 3.5 manages Jackson 2.18+, which Pekko accepts.
  Remove it from your build.

### Migrating from 0.x

1. Upgrade to Java 21 and Spring Boot 3.5 or later. Boot 2 applications stay on 0.x.
2. Remove the `com.fasterxml.jackson:jackson-bom` override from your build.
