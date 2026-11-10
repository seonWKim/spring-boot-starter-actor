package io.github.seonwkim.actor.mixedversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;
import io.github.seonwkim.actor.shard.SpringShardedActorHandle;
import io.github.seonwkim.actor.test.ActorCluster;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * A rolling update in one JVM: node 1 runs version 1 of {@link Hello}, node 2 version 2, which adds a
 * field. Each version must read the other's messages, as the Deployments guide promises.
 */
class MixedVersionMessageTest {

    /** Version 2 of {@link Hello}: the next release adds an optional field. */
    private static final String HELLO_V2 =
            """
            package io.github.seonwkim.actor.mixedversion;

            public record Hello(String text, String language) implements Versioned {
                @Override
                public String describe() {
                    return text + "/" + language;
                }
            }
            """;

    /** What each node's inbox received, by inbox. */
    static final Map<String, Queue<String>> RECEIVED = new ConcurrentHashMap<>();

    @Test
    void nodesOnTwoVersionsOfAMessageReadEachOthersMessages(@TempDir Path classes) throws Exception {
        ClassLoader v2 =
                new HelloV2ClassLoader(compileHelloV2(classes), getClass().getClassLoader());
        try (ActorCluster cluster = ActorCluster.builder(TestApp.class, V1Inbox.class, V2Inbox.class)
                .nodes(2)
                .systemName("mixed-versions")
                .roles(1, "v1")
                .roles(2, "v2")
                .classLoader(2, v2)
                .start()) {
            Versioned newHello = (Versioned) v2.loadClass(Hello.class.getName())
                    .getConstructor(String.class, String.class)
                    .newInstance("hi", "en");
            inbox(cluster, 2, V1Inbox.class).tell(newHello);
            inbox(cluster, 1, V2Inbox.class).tell(new Hello("hello"));

            // The old node drops the field it doesn't know; the new node reads the missing one as null.
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> received(V1Inbox.NAME).contains("hi"));
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> received(V2Inbox.NAME).contains("hello/null"));
        }
    }

    private static Queue<String> received(String inbox) {
        return RECEIVED.getOrDefault(inbox, new ConcurrentLinkedQueue<>());
    }

    private static SpringShardedActorHandle<Versioned> inbox(
            ActorCluster cluster, int fromNode, Class<? extends Inbox> inbox) {
        return cluster.node(fromNode)
                .getBean(SpringActorSystem.class)
                .sharded(inbox)
                .withId("inbox")
                .get();
    }

    private static Path compileHelloV2(Path classes) throws IOException {
        Path source = classes.resolve("src/io/github/seonwkim/actor/mixedversion/Hello.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, HELLO_V2);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        int status = javac.run(
                null,
                null,
                null,
                "-d",
                classes.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                source.toString());
        assertThat(status).as("javac exit status").isZero();
        return classes;
    }

    /** Loads {@link Hello} from version 2's classes, and every other class from the parent. */
    private static final class HelloV2ClassLoader extends ClassLoader {
        private final Path classes;

        HelloV2ClassLoader(Path classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(Hello.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        byte[] bytes = Files.readAllBytes(classes.resolve(name.replace('.', '/') + ".class"));
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return loaded;
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableActorSupport
    static class TestApp {}

    /** Records what it receives; its entities run only on the nodes with its role. */
    abstract static class Inbox implements SpringShardedActor<Versioned> {
        private final String name;

        Inbox(String name) {
            this.name = name;
        }

        @Override
        public ShardedEntityType<Versioned> entityType() {
            return ShardedEntityType.of(Versioned.class, name);
        }

        @Override
        public Optional<String> role() {
            return Optional.of(name);
        }

        @Override
        public SpringActorBehavior<Versioned> create(SpringShardedActorContext<Versioned> ctx) {
            return SpringActorBehavior.builder(Versioned.class, ctx)
                    .onMessage(Versioned.class, (context, message) -> {
                        RECEIVED.computeIfAbsent(name, n -> new ConcurrentLinkedQueue<>())
                                .add(message.describe());
                        return SpringActorBehavior.same();
                    })
                    .build();
        }
    }

    /** Runs on node 1, the old version. */
    public static class V1Inbox extends Inbox {
        static final String NAME = "v1";

        public V1Inbox() {
            super(NAME);
        }
    }

    /** Runs on node 2, the new version. */
    public static class V2Inbox extends Inbox {
        static final String NAME = "v2";

        public V2Inbox() {
            super(NAME);
        }
    }
}
