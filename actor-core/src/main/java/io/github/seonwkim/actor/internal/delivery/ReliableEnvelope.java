package io.github.seonwkim.actor.internal.delivery;

import org.apache.pekko.actor.typed.ActorRef;

/**
 * A reliable message on its way to a {@code ReliableDelivery.consumer}: the message, its idempotency
 * key and where to send the {@link ReliableAck}. Between nodes it is written by actor-cluster's
 * {@code ReliableEnvelopeSerializer}, the message inside with its own serializer.
 */
public record ReliableEnvelope(String idempotencyKey, Object message, ActorRef<ReliableAck> replyTo) {}
