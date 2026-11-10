# Testing

The `actor-test` module has three tools for tests:

- **`@ActorTest`**: a test slice that starts the actor system and your actor beans, and nothing else.
- **`ActorCluster`**: several nodes of your application in one JVM, each on a port it binds itself, for cluster tests.
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

The actor system is the one your application's auto-configuration starts: the slice imports the same
actor auto-configurations, so `spring.actor.*` properties from `application.yml` apply.

## Cluster tests with `ActorCluster`

`ActorCluster` starts several Spring contexts of your application in one JVM. Each node binds port 0, so
the operating system gives it a free port as it binds and no other test or process can take that port in
between. Node 1 joins itself (`spring.actor.cluster.join-self`) and forms the cluster at once; the others
join it at the address it bound. `start()` returns when every node sees every node up. With Pekko
Management on the classpath, each node's management server binds port 0 too.

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
| `classLoader(int, ClassLoader)` | The class loader one node, numbered from 1, loads your classes through. Use it to run two versions of a class side by side |

The properties are passed as command-line arguments, so they override your `application.yml`. The
cluster tracks membership through the starter's cluster events: `upMembers(node)` is the number of members a
node sees up, and `awaitMembersUp(count)` waits until every running node sees that many.

### Two versions of a message in one cluster

To check that a message change survives a rolling update, run one node on the next version of the message
class. Compile the new version into its own folder (for example with `javax.tools.JavaCompiler` in the
test), and give that node a class loader that loads the class from there and every other class from your
application:

=== "Java"

    ```java
    ClassLoader v2 = new ClassLoader(getClass().getClassLoader()) {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(SayHello.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = readClassFile(v2Classes, name); // your helper: the .class file's bytes
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                return loaded;
            }
        }
    };

    try (ActorCluster cluster = ActorCluster.builder(MyApplication.class)
            .nodes(2)
            .classLoader(2, v2)
            .start()) {
        // Node 1 sends the old SayHello to an entity on node 2, and node 2 sends the new one
        // (created through v2.loadClass(...)) to an entity on node 1.
    }
    ```

=== "Kotlin"

    ```kotlin
    val v2 = object : ClassLoader(javaClass.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name != SayHello::class.java.name) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                return findLoadedClass(name) ?: readClassFile(v2Classes, name) // your helper: the .class file's bytes
                    .let { bytes -> defineClass(name, bytes, 0, bytes.size) }
            }
        }
    }

    ActorCluster.builder(MyApplication::class.java)
        .nodes(2)
        .classLoader(2, v2)
        .start()
        .use { cluster ->
            // Node 1 sends the old SayHello to an entity on node 2, and node 2 sends the new one
            // (created through v2.loadClass(...)) to an entity on node 1.
        }
    ```

Put the entities on a given node with a role (`roles(1, "v1")` and `role()` on the sharded actor), so each
message really crosses between the versions. The starter's own build runs this test
(`MixedVersionMessageTest` in `actor-cluster`).

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
