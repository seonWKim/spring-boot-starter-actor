/**
 * At-least-once delivery between actors: the sender retries a message until the receiving actor
 * acknowledges it, and the receiver remembers the idempotency keys it handled so a retry runs the
 * handler only once. Opt-in; plain {@code tell} stays at-most-once.
 *
 * <p>Parameters, return values and fields are non-null unless annotated {@code @Nullable}.
 */
@NonNullApi
@NonNullFields
package io.github.seonwkim.actor.delivery;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
