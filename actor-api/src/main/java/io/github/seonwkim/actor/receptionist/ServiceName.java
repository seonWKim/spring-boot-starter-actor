package io.github.seonwkim.actor.receptionist;

import java.util.Objects;

/**
 * Names a service that actors register under so others can find them, on any node of the cluster.
 *
 * @param messageType the messages the registered actors accept
 * @param id the service id
 * @param <T> the messages the registered actors accept
 */
public record ServiceName<T>(Class<T> messageType, String id) {

    public ServiceName {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public static <T> ServiceName<T> of(Class<T> messageType, String id) {
        return new ServiceName<>(messageType, id);
    }
}
