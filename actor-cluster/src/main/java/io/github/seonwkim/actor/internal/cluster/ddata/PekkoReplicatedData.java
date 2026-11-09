package io.github.seonwkim.actor.internal.cluster.ddata;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.ddata.Consistency;
import io.github.seonwkim.actor.ddata.ReplicatedCounter;
import io.github.seonwkim.actor.ddata.ReplicatedData;
import io.github.seonwkim.actor.ddata.ReplicatedMap;
import io.github.seonwkim.actor.ddata.ReplicatedSet;
import io.github.seonwkim.actor.ddata.ReplicationTimeoutException;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.LWWMap;
import org.apache.pekko.cluster.ddata.LWWMapKey;
import org.apache.pekko.cluster.ddata.ORSet;
import org.apache.pekko.cluster.ddata.ORSetKey;
import org.apache.pekko.cluster.ddata.PNCounter;
import org.apache.pekko.cluster.ddata.PNCounterKey;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;

/**
 * {@link ReplicatedData} on Pekko Distributed Data: a map is an {@code LWWMap}, a counter a {@code
 * PNCounter} and a set an {@code ORSet}. Pekko's own serializers carry them between nodes; keys,
 * values and elements inside go through the actor system's serialization bindings (Jackson for {@code
 * JsonSerializable} and {@code CborSerializable}).
 */
public final class PekkoReplicatedData implements ReplicatedData {

    /** Extra time the ask waits beyond the consistency timeout, so the replicator's own answer wins. */
    private static final Duration ASK_MARGIN = Duration.ofSeconds(5);

    private final SpringActorSystem actorSystem;
    private volatile @Nullable Replication replication;

    public PekkoReplicatedData(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public <K, V> ReplicatedMap<K, V> map(String name) {
        return new PekkoMap<>(name, LWWMapKey.create(name), this);
    }

    @Override
    public ReplicatedCounter counter(String name) {
        return new PekkoCounter(name, PNCounterKey.create(name), this);
    }

    @Override
    public <E> ReplicatedSet<E> set(String name) {
        return new PekkoSet<>(name, ORSetKey.create(name), this);
    }

    /**
     * The replicator, resolved on first use: the bean exists whenever the cluster module is present,
     * also when the actor system runs without cluster, where only using it is an error.
     */
    private Replication replication() {
        Replication current = replication;
        if (current == null) {
            synchronized (this) {
                current = replication;
                if (current == null) {
                    current = Replication.of(PekkoActorSystem.raw(actorSystem));
                    replication = current;
                }
            }
        }
        return current;
    }

    /** The replicator of one actor system and the calls every data type shares. */
    private record Replication(ActorSystem<?> system, ActorRef<Replicator.Command> replicator, SelfUniqueAddress self) {

        static Replication of(ActorSystem<?> system) {
            DistributedData ddata;
            try {
                ddata = DistributedData.get(system);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Replicated data needs cluster mode: set spring.actor.pekko.actor.provider=cluster", e);
            }
            return new Replication(system, ddata.replicator(), ddata.selfUniqueAddress());
        }

        <A extends org.apache.pekko.cluster.ddata.ReplicatedData> CompletionStage<Optional<A>> get(
                Key<A> key, Consistency read) {
            return AskPattern.<Replicator.Command, Replicator.GetResponse<A>>ask(
                            replicator,
                            replyTo -> new Replicator.Get<>(key, readConsistency(read), replyTo),
                            read.timeout().plus(ASK_MARGIN),
                            system.scheduler())
                    .thenApply(response -> {
                        if (response instanceof Replicator.GetSuccess<A> success) {
                            return Optional.of(success.dataValue());
                        }
                        if (response instanceof Replicator.NotFound<A>) {
                            return Optional.empty();
                        }
                        if (response instanceof Replicator.GetFailure<A>) {
                            throw new ReplicationTimeoutException("Reading '" + key.id() + "' did not reach "
                                    + read.level() + " nodes within " + read.timeout());
                        }
                        throw new IllegalStateException("Reading '" + key.id() + "' failed: " + response);
                    });
        }

        <A extends org.apache.pekko.cluster.ddata.ReplicatedData> CompletionStage<Void> update(
                Key<A> key, A empty, Consistency write, Function<A, A> modify) {
            return AskPattern.<Replicator.Command, Replicator.UpdateResponse<A>>ask(
                            replicator,
                            replyTo -> new Replicator.Update<>(key, empty, writeConsistency(write), replyTo, modify),
                            write.timeout().plus(ASK_MARGIN),
                            system.scheduler())
                    .thenApply(response -> {
                        if (response instanceof Replicator.UpdateSuccess<A>) {
                            return null;
                        }
                        if (response instanceof Replicator.UpdateTimeout<A>) {
                            throw new ReplicationTimeoutException("Writing '" + key.id() + "' did not reach "
                                    + write.level() + " nodes within " + write.timeout()
                                    + "; it is applied on this node and spreads later");
                        }
                        if (response instanceof Replicator.ModifyFailure<A> failure) {
                            throw new CompletionException(failure.cause());
                        }
                        throw new IllegalStateException("Writing '" + key.id() + "' failed: " + response);
                    });
        }

        private static Replicator.ReadConsistency readConsistency(Consistency consistency) {
            return switch (consistency.level()) {
                case LOCAL -> Replicator.readLocal();
                case MAJORITY -> new Replicator.ReadMajority(consistency.timeout());
                case ALL -> new Replicator.ReadAll(consistency.timeout());
            };
        }

        private static Replicator.WriteConsistency writeConsistency(Consistency consistency) {
            return switch (consistency.level()) {
                case LOCAL -> Replicator.writeLocal();
                case MAJORITY -> new Replicator.WriteMajority(consistency.timeout());
                case ALL -> new Replicator.WriteAll(consistency.timeout());
            };
        }
    }

    private record PekkoMap<K, V>(String name, Key<LWWMap<K, V>> key, PekkoReplicatedData data)
            implements ReplicatedMap<K, V> {

        @Override
        public CompletionStage<Map<K, V>> entries(Consistency read) {
            return data.replication().get(key, read).thenApply(found -> found.map(map -> Map.copyOf(map.getEntries()))
                    .orElse(Map.of()));
        }

        @Override
        public CompletionStage<Optional<V>> get(K entryKey, Consistency read) {
            Objects.requireNonNull(entryKey, "key");
            return data.replication()
                    .get(key, read)
                    .thenApply(found -> found.flatMap(
                            map -> Optional.ofNullable(map.getEntries().get(entryKey))));
        }

        @Override
        public CompletionStage<Void> put(K entryKey, V value, Consistency write) {
            Objects.requireNonNull(entryKey, "key");
            Objects.requireNonNull(value, "value");
            Replication replication = data.replication();
            return replication.update(key, LWWMap.create(), write, map -> map.put(replication.self(), entryKey, value));
        }

        @Override
        public CompletionStage<Void> remove(K entryKey, Consistency write) {
            Objects.requireNonNull(entryKey, "key");
            Replication replication = data.replication();
            return replication.update(key, LWWMap.create(), write, map -> map.remove(replication.self(), entryKey));
        }
    }

    private record PekkoCounter(String name, Key<PNCounter> key, PekkoReplicatedData data)
            implements ReplicatedCounter {

        @Override
        public CompletionStage<Long> value(Consistency read) {
            return data.replication().get(key, read).thenApply(found -> found.map(
                            counter -> counter.getValue().longValueExact())
                    .orElse(0L));
        }

        @Override
        public CompletionStage<Void> increment(long delta, Consistency write) {
            Replication replication = data.replication();
            return replication.update(
                    key, PNCounter.create(), write, counter -> counter.increment(replication.self(), delta));
        }

        @Override
        public CompletionStage<Void> decrement(long delta, Consistency write) {
            Replication replication = data.replication();
            return replication.update(
                    key, PNCounter.create(), write, counter -> counter.decrement(replication.self(), delta));
        }
    }

    private record PekkoSet<E>(String name, Key<ORSet<E>> key, PekkoReplicatedData data) implements ReplicatedSet<E> {

        @Override
        public CompletionStage<Set<E>> elements(Consistency read) {
            return data.replication().get(key, read).thenApply(found -> found.map(set -> Set.copyOf(set.getElements()))
                    .orElse(Set.of()));
        }

        @Override
        public CompletionStage<Void> add(E element, Consistency write) {
            Objects.requireNonNull(element, "element");
            Replication replication = data.replication();
            return replication.update(key, ORSet.create(), write, set -> set.add(replication.self(), element));
        }

        @Override
        public CompletionStage<Void> remove(E element, Consistency write) {
            Objects.requireNonNull(element, "element");
            Replication replication = data.replication();
            return replication.update(key, ORSet.create(), write, set -> set.remove(replication.self(), element));
        }
    }
}
