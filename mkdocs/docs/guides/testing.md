# Testing

The `actor-test` module has three tools for tests:

- **`@ActorTest`**: a test slice that starts the actor system and your actor beans, and nothing else.
- **`ActorCluster`**: several nodes of your application in one JVM, on free ports, for cluster tests.
- **Contract suites**: the tests every `PeerRegistry` and `LeaseStore` must pass, to run against your own store,
  plus in-memory fakes of both.

Add it as a test dependency:

=== "Gradle"

    ```groovy
    testImplementation 'io.github.seonwkim:actor-test:1.0.0'
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>io.github.seonwkim</groupId>
        <artifactId>actor-test</artifactId>
        <version>1.0.0</version>
        <scope>test</scope>
    </dependency>
    ```

`actor-test` has no Pekko types in its API. It brings JUnit 5 and Spring Boot's test support, not the
starter: your application already has that.

## Test actors with `@ActorTest`

`@ActorTest` works like Spring Boot's `@DataJpaTest` or `@WebMvcTest`. It finds your `@SpringBootApplication`,
turns its auto-configuration off and keeps only actor beans from the component scan. You get the
`SpringActorSystem` and every `SpringActor` and `SpringShardedActor` bean, without a web server, a
`DataSource` or your services. Only configuration properties, Jackson and Spring's task executor are
auto-configured, because the actor system uses them when they are there.

=== "Java"

    ```java
    @ActorTest
    class GreeterActorTest {

        @Autowired
        SpringActorSystem actorSystem;

        @Test
        void greets() throws Exception {
            String reply = actorSystem.actor(GreeterActor.class)
                    .withId("greeter")
                    .spawnAndWait()
                    .ask(new GreeterActor.Greet("Ada"))
                    .execute()
                    .toCompletableFuture()
                    .get();

            assertThat(reply).isEqualTo("hello Ada");
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    @ActorTest
    class GreeterActorTest {

        @Autowired
        lateinit var actorSystem: SpringActorSystem

        @Test
        fun greets() = runBlocking<Unit> {
            val reply: String = actorSystem.actor<GreeterActor, _>()
                .withId("greeter")
                .awaitSpawn()
                .awaitAsk(GreeterActor.Greet("Ada"))

            assertThat(reply).isEqualTo("hello Ada")
        }
    }
    ```

In Kotlin, `runBlocking` (from `kotlinx-coroutines-core`, which `actor-kotlin` brings) runs the `suspend` calls
in the test. Name actors to start only those, and add properties:

=== "Java"

    ```java
    @ActorTest(actors = GreeterActor.class, properties = "spring.actor.name=greeter-test")
    ```

=== "Kotlin"

    ```kotlin
    @ActorTest(actors = [GreeterActor::class], properties = ["spring.actor.name=greeter-test"])
    ```

An actor that needs another bean gets it with `@Import(MyService.class)` or `@MockitoBean`. `includeFilters`,
`excludeFilters` and `excludeAutoConfiguration` work as on Spring Boot's own slices.

The actor system is the one `@EnableActorSupport` configures: the slice imports it the same way your
application does, so `spring.actor.*` properties from `application.yml` apply.

## Cluster tests with `ActorCluster`

`ActorCluster` starts several Spring contexts of your application in one JVM. Each node gets free ports,
node 1 is the only seed node (it forms the cluster at once, the others join it), and `start()` returns when
every node sees every node up. With Pekko Management on the classpath, each node's management port is free
too.

With JUnit 5, register `ActorClusterExtension`. On an instance field it starts a cluster for each test; on a
static field (in Kotlin, a `@JvmField` in a `companion object`), one for the whole class.

=== "Java"

    ```java
    class ShardedCounterClusterTest {

        @RegisterExtension
        final ActorClusterExtension cluster = new ActorClusterExtension(
                ActorCluster.builder(MyApplication.class)
                        .nodes(3)
                        .roles(1, "frontend")
                        .properties("app.feature.enabled=true"));

        @Test
        void anEntityKeepsItsStateWhenANodeLeaves() {
            SpringActorSystem first = cluster.node(1).getBean(SpringActorSystem.class);
            // ... message a sharded entity from node 1

            cluster.cluster().stop(2);
            cluster.cluster().awaitMembersUp(2);
            // ... the entity answers from another node
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    class ShardedCounterClusterTest {

        @JvmField
        @RegisterExtension
        val cluster = ActorClusterExtension(
            ActorCluster.builder(MyApplication::class.java)
                .nodes(3)
                .roles(1, "frontend")
                .properties("app.feature.enabled=true"))

        @Test
        fun anEntityKeepsItsStateWhenANodeLeaves() {
            val first = cluster.node(1).getBean(SpringActorSystem::class.java)
            // ... message a sharded entity from node 1

            cluster.cluster().stop(2)
            cluster.cluster().awaitMembersUp(2)
            // ... the entity answers from another node
        }
    }
    ```

Without JUnit, use it directly; it is `AutoCloseable`:

=== "Java"

    ```java
    try (ActorCluster cluster = ActorCluster.builder(MyApplication.class).nodes(3).start()) {
        // ...
    }
    ```

=== "Kotlin"

    ```kotlin
    ActorCluster.builder(MyApplication::class.java).nodes(3).start().use { cluster ->
        // ...
    }
    ```

| Builder method | What it sets |
|---|---|
| `nodes(int)` | Number of nodes, 3 by default |
| `systemName(String)` | The actor system name of every node |
| `properties(String...)` | `key=value` properties for every node |
| `nodeProperties(int, String...)` | Properties for one node, numbered from 1 |
| `roles(int, String...)` | Cluster roles of one node |
| `timeout(Duration)` | How long to wait for members to be up, 30 seconds by default |

The properties are passed as command-line arguments, so they override your `application.yml`. The
cluster tracks membership through the starter's cluster events: `upMembers(node)` is the number of members a
node sees up, and `awaitMembersUp(count)` waits until every running node sees that many.

## Contract suites for your own store

A `PeerRegistry` or `LeaseStore` of your own ([Cluster Formation](cluster/cluster-formation.md#your-own-store),
[Split Brain and Leases](cluster/split-brain-and-leases.md#your-own-store)) must behave like the built-in ones: for
example, of twenty nodes that try to take a lease at once, exactly one wins. `actor-test` publishes the
tests the built-in JDBC and Redis stores pass. Extend the suite and return your store:

=== "Java"

    ```java
    class MyLeaseStoreContractTest extends LeaseStoreContract {

        private final MyLeaseStore store = new MyLeaseStore(testDatabase());

        @Override
        protected LeaseStore store() {
            return store;
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    class MyLeaseStoreContractTest : LeaseStoreContract() {

        private val store = MyLeaseStore(testDatabase())

        override fun store(): LeaseStore = store
    }
    ```

=== "Java"

    ```java
    class MyPeerRegistryContractTest extends PeerRegistryContract {

        @Override
        protected PeerRegistry registry() {
            return new MyPeerRegistry(testDatabase());
        }
    }
    ```

=== "Kotlin"

    ```kotlin
    class MyPeerRegistryContractTest : PeerRegistryContract() {

        override fun registry(): PeerRegistry = MyPeerRegistry(testDatabase())
    }
    ```

Each test uses its own lease or service name, so one store may serve every test. Some tests wait for a
ttl of one to two seconds to pass, because stores may keep time in whole seconds.

`InMemoryPeerRegistry` and `InMemoryLeaseStore` pass the same suites. Declare one as a bean to run cluster
tests with `discovery.method: custom` or `lease.store: custom` without a database; nodes in one JVM can
share one instance.

## Related

- [Kotlin](kotlin.md): the `suspend` helpers used in the Kotlin tests
- [Cluster Formation](cluster/cluster-formation.md) and [Split Brain and Leases](cluster/split-brain-and-leases.md): the stores the contract suites cover
- [Sharded Actors](cluster/sharded-actors.md)
