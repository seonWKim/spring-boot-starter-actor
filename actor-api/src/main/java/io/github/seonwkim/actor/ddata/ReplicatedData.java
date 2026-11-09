package io.github.seonwkim.actor.ddata;

/**
 * Creates handles to data replicated across the cluster (CRDTs). Inject it, or declare the handles you
 * use as beans:
 *
 * <pre>{@code
 * @Bean
 * ReplicatedMap<String, Cart> carts(ReplicatedData data) {
 *     return data.map("carts");
 * }
 *
 * carts.put("alice", cart, Consistency.majority(Duration.ofSeconds(3)));
 * carts.get("alice").thenAccept(found -> ...);
 * }</pre>
 *
 * <p>A handle is a cheap view: two handles with the same name and kind read and write the same data.
 * Each name holds one kind of data; don't use the same name for a map and a set. Needs the cluster
 * module and {@code spring.actor.pekko.actor.provider=cluster}.
 */
public interface ReplicatedData {

    /** The map replicated under {@code name}. */
    <K, V> ReplicatedMap<K, V> map(String name);

    /** The counter replicated under {@code name}. */
    ReplicatedCounter counter(String name);

    /** The set replicated under {@code name}. */
    <E> ReplicatedSet<E> set(String name);
}
