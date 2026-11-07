# RFC 0008: Add an `@ActorTest` test slice

From row 13 of the [Spring conventions comparison](../spring-conventions.md).

## Status

Proposed

## Problem

A test of one actor loads the whole app with `@SpringBootTest`: web server, data sources, everything.
Spring Boot's slices (`@DataJpaTest` for Spring Data, `@JsonTest`, `@WebMvcTest`) load one layer only. There is
no such slice for actors.

## Proposal

1. Add `@ActorTest` to the test artifact from [RFC 0007](0007-test-artifact.md):

    ```java
    @ActorTest(actors = {OrderActor.class})
    class OrderActorTest {
        @Autowired SpringActorSystem actorSystem;
        @MockBean PaymentClient paymentClient;
    }
    ```

2. Built like Boot's own slices: `@BootstrapWith(SpringBootTestContextBootstrapper.class)`,
   `@ExtendWith(SpringExtension.class)`, `@TypeExcludeFilters(ActorTypeExcludeFilter.class)`,
   `@ImportAutoConfiguration`, and an `@AutoConfigureActor` annotation whose auto-configurations are listed in
   `META-INF/spring/io.github.seonwkim.test.autoconfigure.AutoConfigureActor.imports`.
3. The slice includes the actor auto-configurations and the `ActorTestKit` bean; component scanning
   includes only `SpringActor` and `SpringShardedActor` beans (or only the listed `actors`).
4. Local mode by default, overridable with `properties`.

## Alternatives considered

- **Do nothing.** `@SpringBootTest` keeps working, at the cost of start-up time.
- **Document `@SpringBootTest(classes = ...)` instead.** Works but repeats the auto-configuration list in every
  test and breaks when we add one.

## Risks and rollout

- Needs the auto-configuration classes from [RFC 0001](0001-auto-configuration-registration.md); a slice cannot
  include plain `@Configuration` classes by name.
- The `.imports` file for slices exists since Boot 2.7, so `core`'s Boot 2.7 floor is enough.
- Ships after RFC 0007, in the test artifact only.

## Files it would change

- `test/src/main/java/io/github/seonwkim/test/autoconfigure/ActorTest.java` (new)
- `test/src/main/java/io/github/seonwkim/test/autoconfigure/AutoConfigureActor.java` (new)
- `test/src/main/java/io/github/seonwkim/test/autoconfigure/ActorTypeExcludeFilter.java` (new)
- `test/src/main/resources/META-INF/spring/io.github.seonwkim.test.autoconfigure.AutoConfigureActor.imports` (new)
- `mkdocs/docs/guides/testing.md`
