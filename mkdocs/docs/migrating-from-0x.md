# Migrating from 0.x

1.0 requires **Java 21+ and Spring Boot 3.5+**. Spring Boot 2 users stay on the 0.x line; 0.x keeps
its coordinates (`spring-boot-starter-actor` for Boot 2, `spring-boot-starter-actor_3` for Boot 3).

To move a Boot 3 application from 0.x to 1.0:

1. Upgrade to Java 21 and Spring Boot 3.5 or later.
2. Remove any `com.fasterxml.jackson:jackson-bom` override you added for Pekko. Spring Boot 3.5 manages
   Jackson 2.18+, which Pekko accepts.

The full list of changes, with the migration for each, is in the [changelog](https://github.com/seonwkim/spring-boot-starter-actor/blob/main/CHANGELOG.md).
