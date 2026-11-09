package io.github.seonwkim.actor.spi;

import java.util.Objects;

/**
 * One node of a service, as a {@link PeerRegistry} stores it.
 *
 * @param service the service the node belongs to; nodes that should form one cluster share it
 * @param id the node's unique id within the service (the starter uses the node's cluster address)
 * @param host the host other nodes contact this node on
 * @param port the port other nodes contact this node on (the starter uses the management port)
 */
public record Peer(String service, String id, String host, int port) {
    public Peer {
        requireText(service, "service");
        requireText(id, "id");
        requireText(host, "host");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, got " + port);
        }
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
