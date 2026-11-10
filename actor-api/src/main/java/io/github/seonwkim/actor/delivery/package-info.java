/**
 * At-least-once delivery between actors: a sender keeps each message until the receiving actor
 * confirms it, and sends it again after the receiver crashed or restarted. Opt-in; plain {@code tell}
 * stays at-most-once.
 *
 * <p>Parameters, return values and fields are non-null unless annotated {@code @Nullable}.
 */
@NonNullApi
@NonNullFields
package io.github.seonwkim.actor.delivery;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
